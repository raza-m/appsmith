#!/bin/bash

set -o nounset

CUSTOM_DOMAIN="$1"

# By default, container will use the auto-generate certificate by Let's Encrypt
SSL_CERT_PATH="/etc/letsencrypt/live/$CUSTOM_DOMAIN/fullchain.pem"
SSL_KEY_PATH="/etc/letsencrypt/live/$CUSTOM_DOMAIN/privkey.pem"

# In case of existing custom certificate, container will use them to configure SSL
if [[ -e "/appsmith-stacks/ssl/fullchain.pem" ]] && [[ -e "/appsmith-stacks/ssl/privkey.pem" ]]; then
	SSL_CERT_PATH="/appsmith-stacks/ssl/fullchain.pem"
	SSL_KEY_PATH="/appsmith-stacks/ssl/privkey.pem"
fi

cat <<EOF
map \$http_x_forwarded_proto \$origin_scheme {
  default \$http_x_forwarded_proto;
  '' \$scheme;
}

server {
  listen 80;
  server_name $CUSTOM_DOMAIN;

  return 301 https://\$host\$request_uri;
}

server {
  listen 443 ssl http2;
  server_name _;

  ssl_certificate $SSL_CERT_PATH;
  ssl_certificate_key $SSL_KEY_PATH;


  include /appsmith-stacks/data/certificate/conf/options-ssl-nginx.conf;
  ssl_dhparam /appsmith-stacks/data/certificate/conf/ssl-dhparams.pem;

  client_max_body_size 100m;

  gzip on;

  root /opt/appsmith/editor;
  index index.html index.htm;

  location /.well-known/acme-challenge/ {
    root /appsmith-stacks/data/certificate/certbot;
  }

  proxy_set_header X-Forwarded-Proto \$origin_scheme;
  proxy_set_header X-Forwarded-Host \$host;

  location / {
    try_files \$uri /index.html =404;
  }

  location /api {
    proxy_pass http://localhost:8080;
  }

  location /oauth2 {
    proxy_pass http://localhost:8080;
  }

  location /login {
    proxy_pass http://localhost:8080;
  }

  location /rts {
    proxy_pass http://localhost:8091;
    proxy_http_version 1.1;
    proxy_set_header Host \$host;
    proxy_set_header Connection 'upgrade';
    proxy_set_header Upgrade \$http_upgrade;
  }
}
EOF
