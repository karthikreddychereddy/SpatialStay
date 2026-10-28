package com.spatialstay.media;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile;
import java.io.*; import java.nio.file.*; import java.util.*;
@RestController @RequestMapping("/api/public/media")
public class PublicMediaController {
 private final Path root;
 public PublicMediaController(@Value("${app.upload-dir:uploads}") String uploadDir){root=Paths.get(uploadDir).toAbsolutePath().normalize();}
 @PostMapping(value="/hotel-application",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public Map<String,String> upload(@RequestPart("file") MultipartFile file) throws IOException{
  if(file.isEmpty()) throw new IllegalArgumentException("Image file is empty."); String type=Optional.ofNullable(file.getContentType()).orElse(""); if(!Set.of("image/jpeg","image/png","image/webp").contains(type)) throw new IllegalArgumentException("Only JPG, PNG and WebP images are allowed."); if(file.getSize()>8L*1024*1024) throw new IllegalArgumentException("Each image must be 8 MB or smaller.");
  String ext=type.equals("image/png")?".png":type.equals("image/webp")?".webp":".jpg"; Path dir=root.resolve("hotel-applications");Files.createDirectories(dir);String name=UUID.randomUUID()+ext;Files.copy(file.getInputStream(),dir.resolve(name),StandardCopyOption.REPLACE_EXISTING);return Map.of("url","/uploads/hotel-applications/"+name);
 }
}
