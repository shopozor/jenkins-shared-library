def call(Map params) {
  def helpers = new ch.softozor.pipeline.Helpers()
  pipeline {
    agent any
    environment {  
      BACKEND_APP_NAME = 'shopozor-backend'
      BACKEND_BRANCH = 'dev'
      BACKEND_NAME = credentials("${params.frontendType}-backend-name-credentials") // contains envName + base jps url
      DOCKER_CREDENTIALS = credentials('docker-credentials')
      FRONTEND_APP_NAME = "shopozor-${params.frontendType}-frontend"
      FRONTEND_NAME = credentials("${params.frontendType}-frontend-name-credentials") // contains envName
      IMAGE_TYPE = 'e2e'
      JELASTIC_APP_CREDENTIALS = credentials('jelastic-app-credentials')
      JELASTIC_CREDENTIALS = credentials('jelastic-credentials')
      PATH_TO_TEST_RESULTS = '/home/node'
      SCREENSHOTS_FOLDER = 'screenshots'
      TEST_REPORTS_FOLDER = 'junit-reports'
      VIDEOS_FOLDER = 'videos'
    }
    stages {
      stage('Publishing backend docker image') {
        steps {
          build job: 'backend-publish-docker-image', parameters: [
            booleanParam(name: 'ENABLE_DEV_TOOLS', value: true),
            string(name: 'BRANCH', value: BACKEND_BRANCH),
            string(name: 'IMAGE_TYPE', value: IMAGE_TYPE)
          ]
        }
      }
      stage('Publishing frontend docker image') {
        steps {
          script {
            REPO = "${params.frontendType}-frontend"
            GRAPHQL_API = "http://${BACKEND_NAME_USR}.hidora.com/graphql/"
            ENABLE_DEV_TOOLS = 'true'
            helpers.publishFrontendDockerImage(FRONTEND_APP_NAME, GIT_COMMIT, GRAPHQL_API, ENABLE_DEV_TOOLS, IMAGE_TYPE)
          }
        }
      }
      stage('Starting up backend environment') {
        environment {
          BACKEND_JPS = 'backend.jps'
          E2E_JPS = 'backend-e2e.jps'
          GITHUB_CREDENTIALS = credentials('github-credentials')
        }
        steps {
          script {
            helpers.getBackendE2eManifests(BACKEND_JPS, E2E_JPS, BACKEND_NAME_PSW)
            helpers.deploy(BACKEND_JPS, BACKEND_APP_NAME, BACKEND_NAME_USR, BACKEND_BRANCH, IMAGE_TYPE)
            helpers.resetDatabase(E2E_JPS, BACKEND_NAME_USR, BACKEND_APP_NAME, IMAGE_TYPE)
          }
        }
      }
      stage('Starting up frontend and performing end-to-end tests') {
        environment {
          DOCKER_CREDENTIALS = credentials('docker-credentials')
          DOCKER_REPO = "softozor/${FRONTEND_APP_NAME}"
        }
        steps {
          script {
            FRONTEND_JPS = './common/manifest.jps'
            helpers.deploy(FRONTEND_JPS, FRONTEND_APP_NAME, FRONTEND_NAME, GIT_COMMIT, IMAGE_TYPE)
            
            helpers.mountRemoteFolder(FRONTEND_NAME, 'cp', '/home/node/emails', BACKEND_NAME_USR, 'cp', '/app/emails')
            
            E2E_JPS = './common/e2e/e2e.jps'
            helpers.runE2eTests(E2E_JPS, FRONTEND_NAME, FRONTEND_APP_NAME, IMAGE_TYPE)
          }
        }
      }
      stage('Retrieving test results from frontend environment') {
        steps {
          script {
            def targetNodeGroup = 'cp'
            def targetPath = "/mnt/${FRONTEND_NAME}"
            def sourceNodeGroup = 'cp'
            def jenkinsEnvName = JENKINS_URL.split('/')[2].split(':')[0].split('\\.')[0]
            helpers.retrieveTestResults(jenkinsEnvName, targetNodeGroup, targetPath, FRONTEND_NAME, sourceNodeGroup)
          }
        }
      }
      stage('Building specification') {
        environment {
          SOFTOZOR_CREDENTIALS = credentials('softozor-credentials')
        }
        steps {
          script {
            if(GIT_BRANCH == 'origin/dev' || GIT_BRANCH == 'origin/master') {
              build job: 'frontend-spec', parameters: [
                string(name: 'BRANCH', value: GIT_BRANCH.split('/')[1]),
                string(name: 'REPO', value: params.frontendType)
              ]
            }
          }
        }
      }
    }
    post {
      always {
        script {
          helpers.stopEnvironment(BACKEND_NAME_USR)
          helpers.stopEnvironment(FRONTEND_NAME)
          helpers.buildArtifacts()
        }
      }
    }
  }
}
return this