package org.icgc.argo.workflow_management.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class ParamsFile {
  public static String createParamsFile(String filename, Map<String, Object> params) throws IOException {
    val filePath = String.format("/tmp/%s.json", filename);

    ObjectMapper mapper = new ObjectMapper();
    String jsonResult = mapper.writerWithDefaultPrettyPrinter()
        .writeValueAsString(params);

    Files.write(Paths.get(filePath), jsonResult.getBytes());

    return filePath;
  }
}
