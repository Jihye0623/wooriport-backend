pipeline {
    agent any

    triggers {
        pollSCM('H/5 * * * *')
    }

    environment {
        INSTANCE_ID = 'i-0cb347a8db42a75bc'
        AWS_REGION  = 'ap-northeast-2'
    }

    stages {
        stage('Deploy') {
            steps {
                sh '''
                    COMMAND_ID=$(aws ssm send-command \
                        --region ${AWS_REGION} \
                        --instance-ids ${INSTANCE_ID} \
                        --document-name AWS-RunShellScript \
                        --parameters '{"commands":["export HOME=/root && git config --global --add safe.directory /opt/backend && cd /opt/backend && git fetch origin && git reset --hard origin/service && docker compose up --build -d"]}' \
                        --query "Command.CommandId" \
                        --output text)

                    echo "Command ID: $COMMAND_ID"

                    for i in $(seq 1 20); do
                        sleep 10
                        STATUS=$(aws ssm get-command-invocation \
                            --region ${AWS_REGION} \
                            --command-id $COMMAND_ID \
                            --instance-id ${INSTANCE_ID} \
                            --query "Status" \
                            --output text)
                        echo "[$i] Status: $STATUS"
                        if [ "$STATUS" = "Success" ]; then
                            echo "===== remote stdout (SSM truncates to ~2500 chars) ====="
                            aws ssm get-command-invocation --region ${AWS_REGION} --command-id $COMMAND_ID --instance-id ${INSTANCE_ID} --query "StandardOutputContent" --output text
                            exit 0
                        fi
                        if [ "$STATUS" = "Failed" ] || [ "$STATUS" = "Cancelled" ] || [ "$STATUS" = "TimedOut" ]; then
                            echo "===== DEPLOY FAILED ($STATUS) — remote stdout ====="
                            aws ssm get-command-invocation --region ${AWS_REGION} --command-id $COMMAND_ID --instance-id ${INSTANCE_ID} --query "StandardOutputContent" --output text
                            echo "===== remote stderr ====="
                            aws ssm get-command-invocation --region ${AWS_REGION} --command-id $COMMAND_ID --instance-id ${INSTANCE_ID} --query "StandardErrorContent" --output text
                            exit 1
                        fi
                    done
                    echo "===== TIMEOUT — last remote stdout ====="
                    aws ssm get-command-invocation --region ${AWS_REGION} --command-id $COMMAND_ID --instance-id ${INSTANCE_ID} --query "StandardOutputContent" --output text
                    exit 1
                '''
            }
        }
    }

    post {
        success { echo 'Backend deployed successfully' }
        failure  { echo 'Backend deployment failed' }
    }
}
