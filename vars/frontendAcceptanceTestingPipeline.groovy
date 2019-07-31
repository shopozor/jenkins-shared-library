def call() {
  def helpers = new ch.softozor.pipeline.Helpers()
  pipeline {
    agent {
      docker {
        image 'cypress/base:12.1.0'
      }
    }
    environment {
      REPORTS_FOLDER = 'junit-reports'    
    }
    stages {
      stage('Node Modules Installation') {
        steps {
          sh "CYPRESS_CACHE_FOLDER=$WORKSPACE/.cache yarn"
        }
      }
      stage('Building application') {
        environment {
          GRAPHQL_API = 'http://localhost:8000/graphql/'
        }
        steps {
          sh "yarn build"
        }
      }
      stage('Performing acceptance tests') {
        steps {
          script {
            helpers.deleteFolder(REPORTS_FOLDER)
            sh "CYPRESS_CACHE_FOLDER=$WORKSPACE/.cache yarn start:ci"
          }
        }
      }
    }
    post {
      always {
        junit "**/$REPORTS_FOLDER/*.xml"
        archiveArtifacts artifacts: 'cypress/videos/**/*.mp4, cypress/screenshots/**/*.png'
      }
    }
  }
}