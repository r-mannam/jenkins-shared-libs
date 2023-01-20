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
                //withVault([configuration: config.vault, vaultSecrets: config.secrets]) {
                withSonarQubeEnv('SonarQube') { 
                    script {
                        //sleep(time:3600,unit:"SECONDS")
                        sh "mvn --batch-mode ${config.mvnArgs ?: ''} clean package verify jacoco:report sonar:sonar -Dbranch.name=${env.BRANCH_NAME.replaceAll("/","-")}"
                
                        junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                
                        def tasks = scanForIssues tool: taskScanner(includePattern:'**/*.java', excludePattern:'target/**/*', highTags:'FIXME', normalTags:'TODO')
                        publishIssues issues: [tasks]

                        publishHTML (target: [
                            allowMissing: true,
                            alwaysLinkToLastBuild: true,
                            keepAll: false,
                            reportDir: 'target/site/jacoco',
                            reportFiles: 'index.html',
                            reportName: "JaCoCo Report"
                        ])

                        //cucumber failedFeaturesNumber: -1, failedScenariosNumber: -1, failedStepsNumber: -1, fileIncludePattern: 'target/cucumber.json', pendingStepsNumber: -1, skippedStepsNumber: -1, sortingMethod: 'ALPHABETICAL', undefinedStepsNumber: -1
                        //archiveArtifacts artifacts: 'target/testAccessibility*.txt', allowEmptyArchive: true, fingerprint: false

                        //sh 'sed -i \'/^graphroot /s|=.*$|= "/home/jenkins"|\' /etc/containers/storage.conf'
                        
                        dockerImage = docker.build "${env.DOCKER_REGISTRY}/${config.dockerRepo}:git-${env.GIT_COMMIT.substring(0,7)}"
                    }
                }
                //}
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
                        String argocdTagUpdate = "-p dockerRegistry=${env.DOCKER_REGISTRY} -p dockerRepo=${config.dockerRepo} -p dockerTag=git-${env.GIT_COMMIT.substring(0,7)} -p appName=${config.appName}-${env.BRANCH_NAME.replaceAll("/","-")}"
                        if (config.argocdPlugin) {
                            argocdTagUpdate = "--plugin-env HELM_ARGS='--set dockerRegistry=${env.DOCKER_REGISTRY} --set dockerRepo=${config.dockerRepo} --set dockerTag=git-${env.GIT_COMMIT.substring(0,7)} --set appName=${config.appName}-${env.BRANCH_NAME.replaceAll("/","-")}'"
                        }
                        sh "curl -k -sSL -o /usr/bin/argocd https://${env.ARGOCD_SERVER}/download/argocd-linux-amd64"
                        sh "chmod a+rx /usr/bin/argocd"
                        sh "argocd app set ${config.appName}-${env.BRANCH_NAME.replaceAll("/","-")} ${argocdTagUpdate} --grpc-web --insecure"
                        sh "argocd app sync ${config.appName}-${env.BRANCH_NAME.replaceAll("/","-")} --grpc-web --insecure"
                        sh "argocd app wait ${config.appName}-${env.BRANCH_NAME.replaceAll("/","-")} --grpc-web --insecure"
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
