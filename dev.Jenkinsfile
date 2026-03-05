pipeline {
    agent any

    environment {
        PROJECT_SERVICE  = "API GATEWAY SERVICE 🗃️"
        GIT_BRANCH = "develop"
        GIT_REPO_URL = "https://github.com/Cambofreelance-Software-Development/api-gateway.git"
        GIT_CREDENTIALS_ID = "github_credentials"

        // manifest
        GIT_REPO_MANIFEST_URL = "https://github.com/Cambofreelance-Software-Development/micro-manifest.git"
        GIT_REPO_MANIFEST_UPDATE_URL = "github.com/Cambofreelance-Software-Development/micro-manifest.git"
        GIT_MANIFEST_BRANCH = "dev"
        MANIFEST_FOLDER = "dev/overlays/patches"
        SERVICE_PATCH = "api-gateway-service-patch.yaml"

        IMAGE_REGISTRY = "nexus.cambofreelance.com/docker-hosted"
        FOLDER_REGISTRY = "ms/dev"
        IMAGE_NAME = "api-gateway-service"
        DOCKER_REPO_PATH = "${IMAGE_REGISTRY}/${FOLDER_REGISTRY}/${IMAGE_NAME}"
    }
    stages {
        stage('Checkout Code') {
            steps {
                echo "🔀 Checking out application branch ${env.GIT_BRANCH}"
                git branch:        env.GIT_BRANCH,
                    url:           env.GIT_REPO_URL,
                    credentialsId: env.GIT_CREDENTIALS_ID

                script {
                    // ✅ Call function after checkout
                    def sha = getGitCommitSHA()
                    env.GIT_COMMIT_SHA    = sha
                    env.GIT_COMMIT_SHORT  = sha.take(7)
                    env.DOCKER_FULL_IMAGE = "${env.DOCKER_REPO_PATH}:${sha}"

                    echo "📋 Branch     : ${env.GIT_BRANCH}"
                    echo "📋 Full SHA   : ${env.GIT_COMMIT_SHA}"
                    echo "📋 Short SHA  : ${env.GIT_COMMIT_SHORT}"
                    echo "📋 Docker Tag : ${env.DOCKER_FULL_IMAGE}"
                }
            }
        }

        stage('Docker Build Image') {
            steps {
                script {
                    echo "Build Docker Image"

                    sh '''
                        echo "Current directory:"
                        pwd
                        echo ""

                        echo "Checking Dockerfile..."
                        if [ -f Dockerfile ]; then
                            echo "✅ Dockerfile found"
                            cat Dockerfile
                        else
                            echo "❌ Dockerfile not found!"
                            exit 1
                        fi

                        echo ""
                        echo "Building Docker image: ${DOCKER_FULL_IMAGE}"
                        docker build -t ${DOCKER_FULL_IMAGE} .

                        echo ""
                        echo "✅ Docker images created:"
                        docker images | grep ${DOCKER_FULL_IMAGE}
                    '''
                }
            }
        }
        stage('Push Docker Image') {
            steps {
                script {
                    sh '''
                        echo "✅ Push Docker Image"
                        docker push ${DOCKER_FULL_IMAGE}
                    '''
                }
            }
        }
        stage('Update Manifest') {
            steps {
                script {
                    // Clone repository
                    git branch: "${GIT_MANIFEST_BRANCH}",
                        credentialsId: "${GIT_CREDENTIALS_ID}",
                        url: "${GIT_REPO_MANIFEST_URL}"

                    // Update image tag and push changes
                    withCredentials([usernamePassword(
                        credentialsId: "${GIT_CREDENTIALS_ID}",
                        usernameVariable: 'GIT_USERNAME',
                        passwordVariable: 'GIT_PASSWORD'
                    )]) {
                        sh """
                            cd ${MANIFEST_FOLDER}

                            sed -E -i 's|^([[:space:]]*image:[[:space:]]*).*\$|\\1${DOCKER_FULL_IMAGE}|' "${SERVICE_PATCH}"

                            echo "Updated manifest:"
                            cat "${SERVICE_PATCH}"

                            git config user.email "jenkins@ci.local"
                            git config user.name "Jenkins"
                            git add "${SERVICE_PATCH}"
                            git commit -m "Update ${SERVICE_PATCH} to ${DOCKER_FULL_IMAGE}"
                            git push https://${GIT_USERNAME}:${GIT_PASSWORD}@${GIT_REPO_MANIFEST_UPDATE_URL} HEAD:${GIT_MANIFEST_BRANCH}
                        """
                    }
                }
            }
        }
        stage('Clean Docker Image') {
            steps {
                script {
                    sh '''
                        echo "🧹 Cleaning Docker Image"
                        docker rmi ${FULL_IMAGE_NAME}
                    '''
                }
            }
        }
    }
    post {
        success {
            script {
                sendTelegramNotification("✅ SUCCESS: CI ${PROJECT_SERVICE}")
            }
        }
        failure {
            script {
                sendTelegramNotification("❌ FAILED: CI ${PROJECT_SERVICE}")
            }
        }
        always {
            script {
                echo "🧹 Cleaning workspace..."
                deleteDir()
            }
        }
    }
}
// HELPER FUNCTION
def getGitCommitSHA(boolean shortSha = false) {
    def cmd = shortSha ? 'git rev-parse --short HEAD' : 'git rev-parse HEAD'
    def sha  = sh(script: cmd, returnStdout: true).trim()

    if (!sha) {
        error("❌ Failed to retrieve Git commit SHA")
    }

    return sha
}

def sendTelegramNotification(String message) {
    withCredentials([
        string(credentialsId: 'telegram_bot_token', variable: 'BOT_TOKEN')  // only token is secret
    ]) {
        sh """
            curl -s -X POST https://api.telegram.org/bot\${BOT_TOKEN}/sendMessage \
            -d chat_id=${TELEGRAM_CHAT_ID} \
            -d message_thread_id=${TELEGRAM_TOPIC_ID} \
            -d parse_mode=HTML \
            -d text='${message}'
        """
    }
}