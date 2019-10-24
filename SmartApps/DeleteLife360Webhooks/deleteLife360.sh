#Set variables
username360="username"
password360="PASS"

function bearer {
echo "$(date +%s) INFO: requesting access token"
bearer_id=$(curl -s -X POST -H "Authorization: Basic cFJFcXVnYWJSZXRyZTRFc3RldGhlcnVmcmVQdW1hbUV4dWNyRUh1YzptM2ZydXBSZXRSZXN3ZXJFQ2hBUHJFOTZxYWtFZHI0Vg==" -F "grant_type=password" -F "username=$username360" -F "password=$password360" https://api.life360.com/v3/oauth2/token.json | grep -Po '(?<="access_token":")\w*')
}

function circles () {
echo "$(date +%s) INFO: requesting circles."
read -a circles_id <<<$(curl -s -X GET -H "Authorization: Bearer $1" https://api.life360.com/v3/circles.json | grep -Po '(?<="id":")[\w-]*')
}

function members () {
members=$(curl -s -X GET -H "Authorization: Bearer $1" https://api.life360.com/v3/circles/$2)
echo "$(date +%s) INFO: requesting members"
}

function deleteWebhook() {

}

bearer
circles $bearer_id

#Check if circle id is valid. If not request new token.
if [ -z "$circles_id" ]; then
bearer
circles $bearer_id
fi

#Loop through circle ids
for i in "${circles_id[@]}"
do
echo "$(date +%s) INFO: deleting webhook for $i"
curl -s -X DELETE -H "Authorization: Bearer $bearer_id" https://api.life360.com/v3/circles/$i/webhook.json
done
