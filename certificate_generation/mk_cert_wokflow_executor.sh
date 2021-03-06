cd "$(dirname "$0")" || exit
source "lib_certs.sh"
cd ../src/main/resources/certificates
pwd

# Creates certificates for Workflow executor with the certificates
# in /src/main/resources/certificates/master for the root of Arrowhead
# and /src/main/resources/certificates/cloud for the Local Cloud

create_system_keystore \
  "master/master.p12" "arrowhead.eu" \
  "cloud/testcloud2.p12" "testcloud2.aitia.arrowhead.eu" \
  "workflow_executor_test.p12" "workflow_executor" \
  "dns:localhost,ip:127.0.0.1"

#create_truststore \
#  "cloud-relay/crypto/truststore.p12" \
#  "cloud-root/crypto/root.crt" "arrowhead.eu"

