def commit = "UNKNOWN"
def version = "UNKNOWN"

pipeline {
    agent {
        kubernetes {
            label 'wf-management'
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jdk
    tty: true
    image: openjdk:11
    env:
      - name: DOCKER_HOST
        value: tcp://localhost:2375
  - name: dind-daemon
    image: docker:18.06-dind
    securityContext:
        privileged: true
    volumeMounts:
      - name: docker-graph-storage
        mountPath: /var/lib/docker
  - name: helm
    image: alpine/helm:2.12.3
    command:
    - cat
    tty: true
  - name: docker
    image: docker:18-git
    tty: true
    volumeMounts:
    - mountPath: /var/run/docker.sock
      name: docker-sock
  volumes:
  - name: docker-sock
    hostPath:
      path: /var/run/docker.sock
      type: File
  - name: docker-graph-storage
    emptyDir: {}
"""
        }
    }
    stages {
        stage('Prepare') {
            steps {
                script {
                    commit = sh(returnStdout: true, script: 'git describe --always').trim()
                    version = readMavenPom().getVersion()
                }
            }
        }
        stage('Test') {
            steps {
                container('jdk') {
                    sh "./mvnw test"
                }
            }
        }
        stage('Build & Publish Develop') {
            when {
                branch "develop"
            }
            steps {
                container('docker') {
                    withCredentials([usernamePassword(credentialsId:'argoDockerHub', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        sh 'docker login -u $USERNAME -p $PASSWORD'
                    }

                    // DNS error if --network is default
                    sh "docker build --network=host . -t icgcargo/workflow-management:edge -t icgcargo/workflow-management:${version}-${commit}"

                    sh "docker push icgcargo/workflow-management:${version}-${commit}"
                    sh "docker push icgcargo/workflow-management:edge"
                }
            }
        }
        stage('Release & Tag') {
            when {
                branch "master"
            }
            steps {
                container('docker') {
                    withCredentials([usernamePassword(credentialsId: 'argoGithub', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                        sh "git tag ${version}"
                      sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/icgc-argo/workflow-management --tags"
                    }

                    withCredentials([usernamePassword(credentialsId:'argoDockerHub', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        sh 'docker login -u $USERNAME -p $PASSWORD'
                    }

                    // DNS error if --network is default
                    sh "docker build --network=host . -t icgcargo/workflow-management:latest -t icgcargo/workflow-management:${version}"

                    sh "docker push icgcargo/workflow-management:${version}"
                    sh "docker push icgcargo/workflow-management:latest"
                }
            }
        }
    }
}