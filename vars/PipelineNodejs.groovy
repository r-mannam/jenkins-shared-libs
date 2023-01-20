#!/usr/bin/env groovy

def dockerImage

def call(Map config) {

pipeline {
    agent {
        label "${config.agent}"
    }
    stages {    
        stage('Build') {
            steps {
                withVault([configuration: config.vault, vaultSecrets: config.secrets]) {
                withSonarQubeEnv('SonarQube') { 
                    script {
                        sh "npm install"
                        if (config.lintScript) {
                            sh "npm run ${config.lintScript}"
                        }
                        /*if (config.testScript) {
                            sh "npm run ${config.testScript}"
                        }
                        if (config.testE2eScript) {
                            sh "npm run ${config.testE2eScript}"
                        }
                        if (config.coverageReport) {
                            publishHTML (target: [
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: false,
                                reportDir: "${config.coverageReport}",
                                reportFiles: 'index.html',
                                reportName: "Coverage Report"
                            ])
                        }*/
                        sh "npm run build"
                        /*(String sonarExclusions = ""
                        if (config.sonarExclusions != null) {
                            sonarExclusions = "-Dsonar.exclusions=${config.sonarExclusions}"
                        }   
                        sh "sonar-scanner -Dsonar.projectName=${config.appName}-${env.BRANCH_NAME.replaceAll("/","-")} -Dsonar.projectKey=${config.appName}.${env.BRANCH_NAME.replaceAll("/","-")} ${sonarExclusions}"
   */     
                        dockerImage = docker.build "${env.DOCKER_REGISTRY}/${config.dockerRepo}:git-${env.GIT_COMMIT.substring(0,7)}"
                    }
                }
                }
            }
        }
        
        stage('Scan') {
            when { expression { 
                return (config.performScan) } 
            }
            steps {
                script {
                    prismaCloudScanImage ca: '',
                    cert: '',
                    dockerAddress: 'unix:///var/run/docker.sock',
                    image: "${env.DOCKER_REGISTRY}/${config.dockerRepo}:git-${env.GIT_COMMIT.substring(0,7)}",
                    key: '',
                    logLevel: 'info',
                    podmanPath: '',
                    project: '',
                    resultsFile: 'prisma-cloud-scan-results.json',
                    ignoreImageBuildTime:true

                    prismaCloudPublish resultsFilePattern: 'prisma-cloud-scan-results.json'
                }
            }
        }

        stage('Push') {
            steps {
                script {
                    docker.withRegistry ("https://${env.DOCKER_REGISTRY}", "${env.DOCKER_CREDENTIAL}") {
                        dockerImage.push()
                    }
                }
            }
        }
       
        stage('Deploy') {
            when { expression { 
                return (config.deploy.contains(env.BRANCH_NAME)) } 
            }
            steps {
                withCredentials([string(credentialsId: "${env.ARGOCD_CREDENTIAL}", variable: "ARGOCD_AUTH_TOKEN")]) { 
                    script {
                        String argocdTagUpdate = "-p dockerTag=git-${env.GIT_COMMIT.substring(0,7)}"
                        if (config.argocdPlugin) {
                            argocdTagUpdate = "--plugin-env HELM_ARGS='--set dockerTag=git-${env.GIT_COMMIT.substring(0,7)}'"
                        }
                        sh "curl -k -sSL -o /usr/bin/argocd https://${env.ARGOCD_SERVER}/download/argocd-linux-amd64"
                        sh "chmod a+rx /usr/bin/argocd"
                        sh "argocd app set ${config.appName}-${env.BRANCH_NAME.replaceAll("/","-")} ${argocdTagUpdate} --grpc-web"
                        sh "argocd app sync ${config.appName}-${env.BRANCH_NAME.replaceAll("/","-")} --grpc-web"
                        sh "argocd app wait ${config.appName}-${env.BRANCH_NAME.replaceAll("/","-")} --grpc-web"
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                currentBuild.description = "ImageTag: git-${env.GIT_COMMIT.substring(0,7)}"
            }
        }
    }        
}

}
