def call(Map params) {
  def helpers = new ch.softozor.pipeline.Helpers()
  pipeline {
    agent any
    environment {  
      BACKEND_BRANCH = 'dev'
      BACKEND_NAME = credentials("${params.frontendType}-backend-name-credentials") // contains envName + base jps url
      FRONTEND_NAME = credentials("${params.frontendType}-frontend-name-credentials") // contains envName
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
            string(name: 'BRANCH', value: $BACKEND_BRANCH)
          ]
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
            helpers.prepareBackendConfiguration(BACKEND_JPS, E2E_JPS, BACKEND_NAME_PSW)
            helpers.deploy(BACKEND_JPS, BACKEND_NAME_USR, $BACKEND_BRANCH)
            helpers.resetDatabase(E2E_JPS, BACKEND_NAME_USR)
          }
        }
      }
      stage('Publishing frontend docker image') {
        steps {
          script {
            REPO = "${params.frontendType}-frontend"
            GRAPHQL_API = "http://${BACKEND_NAME_USR}.hidora.com/graphql/"
            ENABLE_DEV_TOOLS = 'true'
            IMAGE_TYPE = 'e2e'
            helpers.publishDockerImage(FRONTEND_NAME, GIT_COMMIT, GRAPHQL_API, ENABLE_DEV_TOOLS, IMAGE_TYPE)
          }
        }
      }
      stage('Starting up frontend and performing end-to-end tests') {
        environment {
          DOCKER_CREDENTIALS = credentials('docker-credentials')
          DOCKER_REPO = "softozor/${FRONTEND_NAME}"
        }
        steps {
          script {
            E2E_JPS = './common/e2e/e2e.jps'
            FRONTEND_JPS = './common/e2e/manifest.jps'
            helpers.prepareFrontendConfiguration(FRONTEND_NAME, FRONTEND_JPS, E2E_JPS)
            // TODO: also make sure that the correct docker image is deployed; 
            //       we only tell what tag to use; the manifest should already have the correct image name
            helpers.deploy(FRONTEND_JPS, FRONTEND_NAME, GIT_COMMIT)
            helpers.runE2eTests(E2E_JPS, FRONTEND_NAME)
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