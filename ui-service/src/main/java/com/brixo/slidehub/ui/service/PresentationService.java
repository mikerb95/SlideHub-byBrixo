package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.model.DriveFile;
import com.brixo.slidehub.ui.model.DriveFolder;
import com.brixo.slidehub.ui.model.Presentation;
import com.brixo.slidehub.ui.model.Slide;
import com.brixo.slidehub.ui.model.SourceType;
import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.PresentationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lógica de negocio para importar y listar presentaciones (PLAN-EXPANSION.md
 * Fase 2).
 *
 * Flujos soportados:
 * - Importación desde Google Drive: descarga imágenes y las sube a S3.
 * - Upload manual: recibe MultipartFile[], los sube directamente a S3.
 *
 * En ambos casos las imágenes van a S3; el filesystem local de Render nunca se
 * usa.
 */
@Service
public class PresentationService {

    private static final Logger log = LoggerFactory.getLogger(PresentationService.class);

    private final PresentationRepository presentationRepository;
    private final GoogleDriveService googleDriveService;
    private final SlideUploadService slideUploadService;

    public PresentationService(PresentationRepository presentationRepository,
            GoogleDriveService googleDriveService,
            SlideUploadService slideUploadService) {
        this.presentationRepository = presentationRepository;
        this.googleDriveService = googleDriveService;
        this.slideUploadService = slideUploadService;
    }

    // ── Listado de presentaciones ─────────────────────────────────────────────

    /**
     * Lista todas las presentaciones de un usuario, ordenadas por fecha de creación
     * desc.
     */
    public List<Presentation> listPresentations(String userId) {
        return presentationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Obtiene una presentación específica asegurando que pertenece al usuario.
     */
    public Optional<Presentation> getPresentation(String userId, String presentationId) {
        return presentationRepository.findByIdAndUserId(presentationId, userId);
    }

    // ── Importación desde Google Drive ────────────────────────────────────────

    /**
     * Lista las carpetas accesibles del Drive del usuario.
     *
     * @param accessToken OAuth2 access token del usuario
     */
    public List<DriveFolder> listDriveFolders(String accessToken) {
        return googleDriveService.listFolders(accessToken);
    }

    /**
     * Lista las imágenes disponibles dentro de una carpeta de Drive.
     *
     * @param folderId    ID de la carpeta en Drive
     * @param accessToken OAuth2 access token del usuario
     */
    public List<DriveFile> listDriveImages(String folderId, String accessToken) {
        return googleDriveService.listImagesInFolder(folderId, accessToken);
    }

    /**
     * Crea una presentación importando imágenes desde Google Drive.
     *
     * Cada imagen se descarga de Drive, se sube a S3 con la clave
     * {@code slides/{presentationId}/{slideNumber}.png} y se persiste una entidad
     * {@link Slide} por imagen.
     *
     * @param user              usuario propietario
     * @param name              nombre de la presentación
     * @param description       descripción (puede ser null)
     * @param driveFolderId     ID de la carpeta de Drive
     * @param driveFolderName   nombre de la carpeta (solo para mostrar en UI)
     * @param repoUrl           URL del repositorio GitHub (puede ser null, usada en
     *                          Fase 3)
     * @param googleAccessToken token OAuth2 de Google del usuario
     * @return presentación creada con todos sus slides
     */
    @Transactional
    public Presentation createFromDrive(User user,
            String name,
            String description,
            String driveFolderId,
            String driveFolderName,
            String repoUrl,
            String googleAccessToken) {
        List<DriveFile> images = googleDriveService.listImagesInFolder(driveFolderId, googleAccessToken);
        if (images.isEmpty()) {
            throw new IllegalArgumentException(
                    "No se encontraron imágenes en la carpeta de Drive: " + driveFolderId);
        }

        Presentation presentation = buildPresentation(user, name, description, repoUrl,
                SourceType.DRIVE, driveFolderId, driveFolderName);
        presentationRepository.save(presentation);

        for (int i = 0; i < images.size(); i++) {
            DriveFile driveFile = images.get(i);
            int slideNumber = i + 1;

            byte[] imageBytes = googleDriveService.downloadImage(driveFile.id(), googleAccessToken);
            if (imageBytes.length == 0) {
                log.warn("Imagen vacía o error al descargar: {} ({})", driveFile.name(), driveFile.id());
                continue;
            }

            String s3Key = "slides/%s/%d.png".formatted(presentation.getId(), slideNumber);
            String contentType = resolveContentType(driveFile.mimeType());
            String s3Url = slideUploadService.upload(s3Key, imageBytes, contentType);

            Slide slide = buildSlide(presentation, slideNumber, slideNumber + ".png",
                    driveFile.id(), s3Url);
            presentation.getSlides().add(slide);
        }

        Presentation saved = presentationRepository.save(presentation);
        log.info("Presentación creada desde Drive: {} ({} slides)", saved.getId(), saved.getSlides().size());
        return saved;
    }

    /**
     * Crea una presentación a partir de archivos subidos manualmente.
     *
     * Los archivos se ordenan por nombre antes de asignarles número de slide.
     * Cada archivo se sube a S3 con la clave
     * {@code slides/{presentationId}/{n}.png}.
     *
     * @param user        usuario propietario
     * @param name        nombre de la presentación
     * @param description descripción (puede ser null)
     * @param repoUrl     URL del repositorio GitHub (puede ser null)
     * @param files       archivos de imagen recibidos vía multipart/form-data
     * @return presentación creada con todos sus slides
     */
    @Transactional
    public Presentation createFromUpload(User user,
            String name,
            String description,
            String repoUrl,
            List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un archivo para crear la presentación.");
        }

        Presentation presentation = buildPresentation(user, name, description, repoUrl,
                SourceType.UPLOAD, null, null);
        presentationRepository.save(presentation);

        // Ordenar por nombre original para mantener un orden predecible
        List<MultipartFile> sorted = files.stream()
                .sorted((a, b) -> {
                    String na = a.getOriginalFilename() != null ? a.getOriginalFilename() : "";
                    String nb = b.getOriginalFilename() != null ? b.getOriginalFilename() : "";
                    return na.compareToIgnoreCase(nb);
                })
                .toList();

        for (int i = 0; i < sorted.size(); i++) {
            MultipartFile file = sorted.get(i);
            int slideNumber = i + 1;

            byte[] imageBytes;
            try {
                imageBytes = file.getBytes();
            } catch (IOException e) {
                log.error("Error leyendo archivo {}: {}", file.getOriginalFilename(), e.getMessage());
                continue;
            }

            String originalFilename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : slideNumber + ".png";
            String contentType = file.getContentType() != null ? file.getContentType() : "image/png";
            String s3Key = "slides/%s/%d.png".formatted(presentation.getId(), slideNumber);
            String s3Url = slideUploadService.upload(s3Key, imageBytes, contentType);

            Slide slide = buildSlide(presentation, slideNumber, slideNumber + ".png",
                    null, s3Url);
            presentation.getSlides().add(slide);
        }

        Presentation saved = presentationRepository.save(presentation);
        log.info("Presentación creada desde upload: {} ({} slides)", saved.getId(), saved.getSlides().size());
        return saved;
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private Presentation buildPresentation(User user,
            String name,
            String description,
            String repoUrl,
            SourceType sourceType,
            String driveFolderId,
            String driveFolderName) {
        Presentation p = new Presentation();
        p.setId(UUID.randomUUID().toString());
        p.setUser(user);
        p.setName(name);
        p.setDescription(description);
        p.setRepoUrl(repoUrl);
        p.setSourceType(sourceType);
        p.setDriveFolderId(driveFolderId);
        p.setDriveFolderName(driveFolderName);
        LocalDateTime now = LocalDateTime.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return p;
    }

    private Slide buildSlide(Presentation presentation,
            int number,
            String filename,
            String driveFileId,
            String s3Url) {
        Slide slide = new Slide();
        slide.setId(UUID.randomUUID().toString());
        slide.setPresentation(presentation);
        slide.setNumber(number);
        slide.setFilename(filename);
        slide.setDriveFileId(driveFileId);
        slide.setS3Url(s3Url);
        slide.setUploadedAt(LocalDateTime.now());
        return slide;
    }

    /**
     * Determina el content-type para S3 basado en el MIME type reportado por Drive.
     */
    private String resolveContentType(String mimeType) {
        if (mimeType == null)
            return "image/png";
        return switch (mimeType) {
            case "image/jpeg" -> "image/jpeg";
            case "image/gif" -> "image/gif";
            case "image/webp" -> "image/webp";
            default -> "image/png";
        };
    }
}
