package org.icgc.argo.workflow_management.util;

import lombok.val;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NextflowConfigFile {
  public static String createNextflowConfigFile(
      String filename, Optional<String> projectDir, Optional<String> workDir) throws IOException {
    val filePath = String.format("/tmp/%s.config", filename);

    File configFile = new File(filePath);
    FileWriter writer = new FileWriter(configFile);

    // Construct file contents
    List<String> fileContent = new ArrayList<>();

    fileContent.add("k8s {");
    projectDir.ifPresent(text -> fileContent.add(String.format("\tprojectDir = '%s'", text)));
    workDir.ifPresent(text -> fileContent.add(String.format("\tworkDir = '%s'", text)));
    fileContent.add("}");

    // Write contents to file
    for (String line : fileContent) {
      writer.write(line + System.lineSeparator());
    }

    // write newline at end of file and close
    writer.write(String.format("%n"));
    writer.flush();
    writer.close();

    // Return path for usage in nextflow
    return filePath;
  }
}
