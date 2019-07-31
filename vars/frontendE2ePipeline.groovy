def call(Map params) {
  def helpers = new ch.softozor.pipeline.Helpers()
  pipeline {
    agent any
    environment {  
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
      stage('Starting up backend environment') {
        environment {
          GITHUB_CREDENTIALS = credentials('github-credentials')
          BACKEND_JPS = 'backend.jps'
          E2E_JPS = 'backend-e2e.jps'
        }
        steps {
          script {
            helpers.prepareBackendConfiguration(GITHUB_CREDENTIALS_USR, GITHUB_CREDENTIALS_PSW, 'dev', BACKEND_JPS, E2E_JPS, BACKEND_NAME_PSW)
            helpers.deploy(BACKEND_JPS, BACKEND_NAME_USR)
            helpers.resetDatabase(E2E_JPS, BACKEND_NAME_USR)
          }
        }
      }
      stage('Building frontend app') {
        environment {
          GRAPHQL_API = "http://${BACKEND_NAME_USR}.hidora.com/graphql/"
        }
        steps {
          sh "yarn && yarn build"
        }
      }
      stage('Starting up frontend and performing end-to-end tests') {
        environment {
          DOCKER_CREDENTIALS = credentials('docker-credentials')
          DOCKER_REPO = "softozor/${FRONTEND_NAME}"
        }
        steps {
          script {
            def E2E_JPS = './common/e2e/e2e.jps'
            def FRONTEND_JPS = './common/e2e/manifest.jps'
            helpers.buildDockerImage()
            helpers.prepareFrontendConfiguration(FRONTEND_NAME, FRONTEND_JPS, E2E_JPS)
            helpers.deploy(FRONTEND_JPS, FRONTEND_NAME)
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