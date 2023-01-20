#!/usr/bin/env groovy

def call() {


pipeline {
    agent {
        label "jenkins-jenkins-agent"
    }
    stages {
        stage('Promote') {
            steps {
                withCredentials([string(credentialsId: "${env.ARGOCD_CREDENTIAL}", variable: "ARGOCD_AUTH_TOKEN")]) { 
                    script {
                        String argocdTagUpdate = "-p dockerTag=${params.ImageTag}"
                        sh "curl -k -sSL -o /usr/bin/argocd https://${env.ARGOCD_SERVER}/download/argocd-linux-amd64"
                        sh "chmod a+rx /usr/bin/argocd"
                        sh "argocd app set ${params.Application}-${params.Environment} ${argocdTagUpdate} --grpc-web"
                        sh "argocd app sync ${params.Application}-${params.Environment} --grpc-web"
                        sh "argocd app wait ${params.Application}-${params.Environment} --grpc-web"
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                currentBuild.description = "Application: ${params.Application}\n" + 
                                           "Namespace: ${params.Environment}\n" + 
                                           "ImageTag: ${params.ImageTag}"
                //build job: 'pipelineA', parameters: [string(name: 'param1', value: "value1")]
                if ("stage".equals(params.Environment)) {
                    build job: '/speed-code-challenge-e2e-tests'
                }
            }
        }
    }
}

}


