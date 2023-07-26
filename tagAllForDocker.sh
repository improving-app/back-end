for dockerfile in "improving-app-gateway" "improving-app-tenant" "improving-app-organization" "improving-app-member" "improving-app-store" "improving-app-event" "improving-app-product"; do
  docker tag $dockerfile\:latest weinyopp/$dockerfile\:latest
  docker push weinyopp/$dockerfile\:latest
done