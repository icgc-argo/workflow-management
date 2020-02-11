package org.icgc.argo.workflow_management.util;

import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.regex.Pattern.compile;

@Builder
@RequiredArgsConstructor
public class GithubFileUrlGenerator implements Callable<String> {

//  private static final String URL_FORMAT = "https://github.com/%s/%s/blob/%s/%s";
  private static final String URL_FORMAT = "%s/blob/%s/%s";
  private static final Pattern GITHUB_ROOT_URL_PATTERN = compile("^https:\\/\\/github.com\\/([^\\/]+)\\/([^\\/]+)[\\/]*$");

  @NonNull
//  @Pattern(regexp = "^https:\\/\\/github.com\\/[^\\/]+\\/[^\\/]+[\\/]*$")
  private final String githubRootUrl;
  private final String branch;
  @NonNull private final String filePath;

  //TODO: rtisma
  private String resolveBranch(){
    return isNullOrEmpty(branch) ? "develop" : branch;
  }

  public static GithubFileUrlGenerator.GithubFileUrlGeneratorBuilder builderFrom(String githubUrl){
    val matcher = GITHUB_ROOT_URL_PATTERN.matcher(githubUrl);
//    matcher.matches()
    return null;



  }

  @Override
  public String call() {
    return format(URL_FORMAT, githubRootUrl, resolveBranch(), filePath);
  }
}
