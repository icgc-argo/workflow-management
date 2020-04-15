package org.icgc.argo.workflow_management.util;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.val;

public class NextflowConfigFile {
  public static String createNextflowConfigFile(
      @NonNull String filename, String launchDir, String projectDir, String workDir)
      throws IOException {
    val filePath = String.format("/tmp/%s.config", filename);

    File configFile = new File(filePath);
    FileWriter writer = new FileWriter(configFile);

    // Construct file contents
    List<String> fileContent = new ArrayList<>();

    fileContent.add("k8s {");
    writeFormattedLineIfValue(fileContent::add, "\tlaunchDir = '%s'", launchDir);
    writeFormattedLineIfValue(fileContent::add, "\tprojectDir = '%s'", projectDir);
    writeFormattedLineIfValue(fileContent::add, "\tworkDir = '%s'", workDir);
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

  private static void writeFormattedLineIfValue(
      @NonNull Consumer<String> consumer, @NonNull String formatted, String value) {
    if (!isNullOrEmpty(value)) {
      consumer.accept(String.format(formatted, value));
    }
  }
}
