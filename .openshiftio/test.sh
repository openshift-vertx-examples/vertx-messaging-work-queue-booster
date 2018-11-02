#!/usr/bin/env bash
set -e

source .openshiftio/openshift.sh

if [ ! -d ".openshiftio" ]; then
  warning "The script expects the .openshiftio directory to exist"
  exit 1
fi

# cleanup
oc delete build --all
oc delete bc --all
oc delete dc --all
oc delete deploy --all
oc delete is --all
oc delete istag --all
oc delete isimage --all
oc delete job --all
oc delete po --all
oc delete rc --all
oc delete rs --all
oc delete statefulsets --all
oc delete secrets --all
oc delete configmap --all
oc delete services --all
oc delete routes --all
oc delete template --all

# Deploy the templates and required resources
oc apply -f frontend/.openshiftio/service.yaml
oc apply -f frontend/.openshiftio/application.yaml
oc apply -f worker/.openshiftio/application.yaml

# Create the application
oc new-app --template=vertx-messaging-frontend \
    -p SOURCE_REPOSITORY_URL=https://github.com/openshiftio-vertx-boosters/vertx-messaging-work-queue-booster \
    -p SOURCE_REPOSITORY_DIR=frontend

oc new-app --template=vertx-messaging-worker \
    -p SOURCE_REPOSITORY_URL=https://github.com/openshiftio-vertx-boosters/vertx-messaging-work-queue-booster \
    -p SOURCE_REPOSITORY_DIR=worker

# wait for pods to be ready
waitForPodState "work-queue-broker-amq" "Running"
waitForPodReadiness "work-queue-broker-amq" 1
waitForPodState "vertx-messaging-worker" "Running"
waitForPodReadiness "vertx-messaging-worker" 1
waitForPodState "vertx-messaging-frontend" "Running"
waitForPodReadiness "vertx-messaging-frontend" 1

cd integration-tests; mvn verify -Popenshift-it -Denv.init.enabled=false;
cd .. || exit
