# LinguaVoice frontend: static files served by Nginx, which also proxies /api to the backend.
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
# Only what the browser needs: no tests, dev server or package.json.
COPY *.html /usr/share/nginx/html/
COPY css /usr/share/nginx/html/css
COPY js /usr/share/nginx/html/js
COPY assets /usr/share/nginx/html/assets

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -q -O /dev/null http://localhost/ || exit 1
