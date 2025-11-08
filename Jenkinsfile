// ===================================
// SHARED LIBRARY FUNCTIONS UPDATE
// ===================================
def sendTelegramNotification(String status, String buildNumber, String branch, String commitMsg, String files, String user) {
    def emoji = status == 'SUCCESS' ? '🟢' : '🔴'
    // Use explicit newlines and proper escaping
    def messageText = "${emoji} API GATEWAY ${status} ( UAT )\n" +
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
            def buildResult = sh(script: "docker build -t ${imagePath} .", returnStatus: true)
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
        def buildResult = sh(script: "docker build -t ${imagePath} .", returnStatus: true)
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

def deleteAllDockerImages(String imageName = null) {
    // Use parameter or fall back to environment variable
    def targetImage = imageName ?: "${env.DOCKER_REPO_PATH}/${env.PROJECT_NAME}"

    echo "🗑️ Deleting all images for: ${targetImage}"

    sh """
        # Show images before deletion
        echo "📋 Images to be deleted:"
        docker images '${targetImage}' 2>/dev/null || echo "No images found"

        # Delete all images with any tag
        docker images '${targetImage}' -q | xargs -r docker rmi -f

        # Also try with wildcard
        docker images '${targetImage}:*' -q | xargs -r docker rmi -f

        echo "✅ All ${targetImage} images deleted"
    """
}


def updateKubernetesManifests(String imageTag, String imagePath,String manifestPath) {
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

            dir("${manifestPath}") {
                echo "📄 Updating ${manifestPath}/kustomization.yaml → newTag: ${imageTag}"
                sh "echo 'Current kustomization.yaml:' && cat kustomization.yaml"
                sh "sed -i 's/newTag: .*/newTag: \"${imageTag}\"/g' kustomization.yaml"
                sh "echo 'Updated kustomization.yaml:' && cat kustomization.yaml"
            }
            sh "git add ${manifestPath}/kustomization.yaml"

            sh """
                if git diff --cached --quiet; then
                    echo "No changes to commit"
                else
                    git commit -m 'ci: update API image tag to ${imageTag}'
                    git push https://${GITHUB_USER}:${GITHUB_TOKEN}@${repoUrl} HEAD:${env.MANIFESTS_BRANCH}
                fi
            """
        }
    }
}

def deployToServer(String serverIp, String containerName, String imagePath, String appPort) {
    echo "🚀 Deploying to server ${serverIp}"

    withCredentials([sshUserPrivateKey(
        credentialsId: 'jenkins-ssh-key', // You need to create this in Jenkins
        keyFileVariable: 'SSH_KEY',
        usernameVariable: 'SSH_USER'
    )]) {
        sh """
            # Test SSH connection
            ssh -o StrictHostKeyChecking=no -i \${SSH_KEY} \${SSH_USER}@${serverIp} 'echo "SSH connection successful"'

            # Stop and remove existing container if it exists
            ssh -o StrictHostKeyChecking=no -i \${SSH_KEY} \${SSH_USER}@${serverIp} '
                if docker ps -a --format "{{.Names}}" | grep -q "^${containerName}\$"; then
                    echo "Stopping existing container..."
                    docker stop ${containerName} || true
                    echo "Removing existing container..."
                    docker rm ${containerName} || true
                fi
            '

            # Pull the latest image
            echo "📥 Pulling Docker image..."
            ssh -o StrictHostKeyChecking=no -i \${SSH_KEY} \${SSH_USER}@${serverIp} '
                docker pull ${imagePath}
            '

            # Run new container
            echo "🐳 Starting new container..."
            ssh -o StrictHostKeyChecking=no -i \${SSH_KEY} \${SSH_USER}@${serverIp} '
                docker run -d \
                    -p ${appPort} \
                    --name ${containerName} \
                    --restart=unless-stopped \
                    ${imagePath}
            '

            # Verify container is running
            echo "✅ Verifying deployment..."
            ssh -o StrictHostKeyChecking=no -i \${SSH_KEY} \${SSH_USER}@${serverIp} '
                docker ps --filter "name=${containerName}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
            '

            # Cleanup old images (optional)
            echo "🧹 Cleaning up old images..."
            ssh -o StrictHostKeyChecking=no -i \${SSH_KEY} \${SSH_USER}@${serverIp} '
                docker image prune -f
            '
        """
    }

    echo "✅ Deployment completed successfully"
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
        // Docker Image Registry: cambo-registry.cambofreelance.com/ms/car-api:${env.BUILD_NUMBER}
        HARBOR_CREDENTIALS_ID = 'harbor-lottery-credentials'
        DOCKER_REGISTRY = 'cambo-registry.cambofreelance.com'
        HARBOR_FOLDER = 'ms'
        DOCKER_REPO_PATH = "${env.DOCKER_REGISTRY}/${env.HARBOR_FOLDER}"
        DOCKER_FULL_IMAGE = "${env.DOCKER_REPO_PATH}/api-gateway:${env.BUILD_NUMBER}"

        // Kubernetes Manifests
        MANIFESTS_REPO_URL = 'https://github.com/Cambofreelance-Software-Development/api-gateway.git'
        MANIFESTS_DIR = 'k8s-manifests'
        MANIFESTS_BRANCH = 'main'
        MANIFESTS_PATH = "overlays/prod"

        // Telegram Bot for Notifications
        TELEGRAM_BOT_TOKEN = '7222744482:AAH_zHNe_L-O2uSiPOpLkkiNkcUc6oSG-Uw'
        TELEGRAM_CHAT_ID   = '-4838394467'

        // GitHub Credentials
        GITHUB_CREDENTIALS_ID = 'GitHub-Credentials'
        GITHUB_REPO_URL = 'https://github.com/Cambofreelance-Software-Development/api-gateway-manifest.git'

        // Pipeline Config
        TARGET_BRANCH = 'main'

        // SSH Deployment
        SERVER_IP = "62.146.239.183"

        // APPLICATION INFO
        CONTAINER_NAME = "ms-api-gateway"
        APPLICATION_PORT = "25010:25010"
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
                    buildAndPushImage(env.DOCKER_FULL_IMAGE)
                }
            }
        }

        stage('Update Kubernetes Manifests') {
            steps {
                script {
                    updateKubernetesManifests(env.BUILD_NUMBER, env.DOCKER_FULL_IMAGE,env.MANIFESTS_PATH)
                }
            }
        }

        stage('Deploy to Server') {
            steps {
                script {
                    deployToServer(
                        env.SERVER_IP,
                        env.CONTAINER_NAME,
                        env.DOCKER_FULL_IMAGE,
                        env.APPLICATION_PORT
                    )
                }
            }
        }

        stage('Cleanup docker image') {
            steps {
                script {
                    deleteAllDockerImages()
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

