// ===================================
// SHARED LIBRARY FUNCTIONS
// ===================================
def sendTelegramNotification(String status, String buildNumber, String branch, String commitMsg, String files, String user) {
    def emoji = status == 'SUCCESS' ? '🟢' : '🔴'
    def messageText = "${emoji} API Gateway ${status} ( UAT )\n" +
                     "🏗️ Job: ${buildNumber}\n" +
                     "🌿 Branch: ${branch}\n" +
                     "📝 Commit: ${commitMsg}\n" +
                     "📁 Changed Files: ${files}\n" +
                     "👤 Triggered by: ${user}"

    def escapedMessage = messageText.replaceAll('"', '\\\\"').replaceAll('\n', '\\\\n')

    sh """
        curl -s -X POST "https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage" \
        -H 'Content-Type: application/json' \
        -d '{"chat_id":"${env.TELEGRAM_CHAT_ID}","text":"${escapedMessage}"}'
    """
}

def loginToHarbor() {
    withCredentials([usernamePassword(
        credentialsId: env.HARBOR_CREDENTIALS_ID,
        usernameVariable: 'HARBOR_USER',
        passwordVariable: 'HARBOR_PASSWORD'
    )]) {
        sh """
            echo \$HARBOR_PASSWORD | docker login ${env.DOCKER_REGISTRY} -u \$HARBOR_USER --password-stdin
        """
    }
    echo "✅ Successfully logged in to Harbor registry"
}

def buildAndPushImage(String imagePath, String buildContext = '.') {
    if (buildContext != '.') {
        dir(buildContext) {
            echo "📁 Current working directory:"
            sh "pwd && ls -la"

            echo "🐳 Building image ${imagePath}"
            def buildResult = sh(script: "docker build --pull --no-cache -t ${imagePath} .", returnStatus: true)
            if (buildResult != 0) {
                error("Docker build failed")
            }

            echo "⬆️ Pushing image ${imagePath}"
            def pushResult = sh(script: "docker push ${imagePath}", returnStatus: true)
            if (pushResult != 0) {
                error("Docker push failed")
            }
        }
    } else {
        echo "📁 Current working directory:"
        sh "pwd && ls -la"

        echo "🐳 Building image ${imagePath}"
        def buildResult = sh(script: "docker build --pull --no-cache -t ${imagePath} .", returnStatus: true)
        if (buildResult != 0) {
            error("Docker build failed")
        }

        echo "⬆️ Pushing image ${imagePath}"
        def pushResult = sh(script: "docker push ${imagePath}", returnStatus: true)
        if (pushResult != 0) {
            error("Docker push failed")
        }
    }
    echo "✅ Docker image ${imagePath} built and pushed successfully"
}

def updateKubernetesManifests(String imageTag, String imagePath) {
    def repoUrl = env.MANIFESTS_REPO_URL.replace('https://', '')
    withCredentials([usernamePassword(
        credentialsId: env.GITHUB_CREDENTIALS_ID,
        usernameVariable: 'GITHUB_USER',
        passwordVariable: 'GITHUB_TOKEN'
    )]) {
        echo "📦 Cloning k8s-manifest repo"
        sh """
            rm -rf ${env.MANIFESTS_DIR}
            git clone https://${GITHUB_USER}:${GITHUB_TOKEN}@${repoUrl} ${env.MANIFESTS_DIR}
        """

        dir(env.MANIFESTS_DIR) {
            sh 'git config user.email "sochoeun1202@gmail.com"'
            sh 'git config user.name "sochoeun"'
            sh "git checkout ${env.MANIFESTS_BRANCH} || git checkout -b ${env.MANIFESTS_BRANCH}"

            dir('overlays/prod') {
                echo "📄 Updating overlays/prod/kustomization.yaml → newTag: ${imageTag}"
                sh "echo 'Current kustomization.yaml:' && cat kustomization.yaml"
                sh "sed -i 's/newTag: .*/newTag: \"${imageTag}\"/g' kustomization.yaml"
                sh "echo 'Updated kustomization.yaml:' && cat kustomization.yaml"
            }
            sh "git add overlays/prod/kustomization.yaml"

            sh """
                if git diff --cached --quiet; then
                    echo "No changes to commit"
                else
                    git commit -m 'ci: update WEB image tag to ${imageTag}'
                    git push https://${GITHUB_USER}:${GITHUB_TOKEN}@${repoUrl} HEAD:${env.MANIFESTS_BRANCH}
                fi
            """
        }
    }
}

def cleanupDockerImages(String imagePath) {
    echo "🧹 Cleaning up local Docker image"
    sh """
        docker rmi ${imagePath} || true
        docker image prune -f || true
    """
}

// ===================================
// UAT PIPELINE (Automated)
// ===================================
pipeline {
    agent any

    triggers {
        githubPush()
        pollSCM('H/5 * * * *') // Poll every 5 minutes as backup
    }

    environment {
        // Docker Image Registry
        HARBOR_CREDENTIALS_ID = 'harbor-lottery-credentials'
        DOCKER_REGISTRY = 'cambo-registry.cambofreelance.com'
        HARBOR_FOLDER = 'keycloak'
        DOCKER_REPO_PATH = "${env.DOCKER_REGISTRY}/${env.HARBOR_FOLDER}"
        DOCKER_FULL_IMAGE = "${env.DOCKER_REPO_PATH}/api-gateway:${env.BUILD_NUMBER}"

        // Kubernetes Manifests
        MANIFESTS_REPO_URL = 'https://github.com/Cambofreelance-Software-Development/api-gateway-manifest.git'
        MANIFESTS_DIR = 'k8s-manifests'
        MANIFESTS_BRANCH = 'main'

        // Telegram Bot for Notifications
        TELEGRAM_BOT_TOKEN = '7222744482:AAH_zHNe_L-O2uSiPOpLkkiNkcUc6oSG-Uw'
        TELEGRAM_CHAT_ID   = '-4838394467'

        // GitHub Credentials
        GITHUB_CREDENTIALS_ID = 'GitHub-Credentials'
        GITHUB_REPO_URL = 'https://github.com/Cambofreelance-Software-Development/api-gateway.git'

        // Pipeline Config
        TARGET_BRANCH = 'main'
    }

    stages {
        stage('Checkout Code') {
            steps {
                echo "📁 Checking out application branch ${env.TARGET_BRANCH}"
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${env.TARGET_BRANCH}"]],
                    userRemoteConfigs: [[
                        url: env.GITHUB_REPO_URL,
                        credentialsId: env.GITHUB_CREDENTIALS_ID
                    ]]
                ])
            }
        }

        stage('Login to Harbor') {
            steps {
                script {
                    loginToHarbor()
                }
            }
        }

        stage('Build & Push Docker Image') {
            steps {
                script {
                    buildAndPushImage(env.DOCKER_FULL_IMAGE, '.')
                }
            }
        }

        stage('Update Kubernetes Manifests') {
            steps {
                script {
                    updateKubernetesManifests(env.BUILD_NUMBER, env.DOCKER_FULL_IMAGE)
                }
            }
        }

        stage('Cleanup') {
            steps {
                script {
                    cleanupDockerImages(env.DOCKER_FULL_IMAGE)
                }
            }
        }
    }

    post {
        success {
            script {
                wrap([$class: 'BuildUser']) {
                    def userName = env.BUILD_USER_ID ?: env.BUILD_USER ?: "Unknown User"
                    def commitMessage = sh(script: "git log -1 --pretty=%B", returnStdout: true).trim()
                    def changedFiles = sh(script: "git diff --name-only HEAD~1 HEAD", returnStdout: true).trim()

                    sendTelegramNotification(
                        'SUCCESS',
                        env.BUILD_NUMBER,
                        env.GIT_BRANCH,
                        commitMessage,
                        changedFiles,
                        userName
                    )
                }
            }
        }
        failure {
            script {
                wrap([$class: 'BuildUser']) {
                    def userName = env.BUILD_USER_ID ?: env.BUILD_USER ?: "Unknown User"
                    def commitMessage = sh(script: "git log -1 --pretty=%B", returnStdout: true).trim()
                    def changedFiles = sh(script: "git diff --name-only HEAD~1 HEAD", returnStdout: true).trim()

                    sendTelegramNotification(
                        'FAILURE',
                        env.BUILD_NUMBER,
                        env.GIT_BRANCH,
                        commitMessage,
                        changedFiles,
                        userName
                    )
                }
            }
        }
    }
}