@Library(value='jenkins-pipeline-library@master', changelog=false) _
pipelineRDPCWorkflowManagement(
    buildImage: "openjdk:11",
    dockerRegistry: "ghcr.io",
    dockerRepo: "icgc-argo/workflow-management",
    gitRepo: "icgc-argo/workflow-management",
    testCommand: "./mvnw test --quiet",
    helmRelease: "management"
)
