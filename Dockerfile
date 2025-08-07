# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn dependency:resolve && \
    mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-noble
WORKDIR /app

# Install system dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    wget \
    libatomic1 \
    libicu74 \
    libssl3 \
    libcrypto++8 \
    libsodium23 \
    libcurl4 \
    libmediainfo0v5 \
    libzen0v5 \
    libavformat60 \
    libavutil58 \
    libavcodec60 \
    libswscale7 \
    libswresample4 \
    libfreeimage3 \
    libglib2.0-0 \
    libjpeg8 \
    libopenjp2-7 \
    libpng16-16 \
    libraw23 \
    libtiff6 \
    libwebpmux3 \
    libopenexr-3-1-30 \
    libimath-3-1-29 \
    libzstd1 \
    libtinyxml2-10 \
    libxml2 \
    libbz2-1.0 \
    libgme0 \
    libopenmpt0t64 \
    libchromaprint1 \
    libbluray2 \
    librabbitmq4 \
    librist4 \
    libsrt1.5-gnutls \
    libzmq5 \
    libva-drm2 \
    libva2 \
    libva-x11-2 \
    libvdpau1 \
    libx11-6 \
    libdrm2 \
    libvpl2 \
    libvpx9 \
    liblzma5 \
    libdav1d7 \
    librsvg2-2 \
    libcairo2 \
    libzvbi0 \
    libsnappy1v5 \
    libaom3 \
    libcodec2-1.2 \
    libgsm1 \
    libjxl0.7 \
    libmp3lame0 \
    libopus0 \
    librav1e0 \
    libshine3 \
    libspeex1 \
    libsvtav1enc1d1 \
    libtheora0 \
    libtwolame0 \
    libvorbis0a \
    libvorbisenc2 \
    libwebp7 \
    libx264-164 \
    libx265-199 \
    libxvidcore4 \
    liblcms2-2 \
    libgomp1 \
    liblerc4 \
    libjbig0 \
    libdeflate0 \
    libpcre2-8-0 \
    libfontconfig1 \
    libfreetype6 \
    libudfread0 \
    libcjson1 \
    libgpg-error0 \
    libbsd0 \
    libsoxr0 \
    libcairo-gobject2 \
    libgdk-pixbuf-2.0-0 \
    libgio-2.0-0 \
    libpangocairo-1.0-0 \
    libpango-1.0-0 \
    libpangoft2-1.0-0 \
    libharfbuzz0b \
    libfribidi0 \
    libthai0 \
    libgraphite2-3 \
    libdatrie1 \
    && rm -rf /var/lib/apt/lists/*

# Install libsodium26 manually
RUN wget -O /tmp/libsodium26.deb http://deb.debian.org/debian/pool/main/libs/libsodium/libsodium26_1.0.19-1_amd64.deb && \
    dpkg -i /tmp/libsodium26.deb && \
    rm /tmp/libsodium26.deb

# Download libmega.so
RUN wget -O /usr/lib/libmega.so \
    "https://github.com/CommonGrounds/CyclingPower_Server/releases/download/v1.1-libmega/libmega.so" && \
    chmod +x /usr/lib/libmega.so

# Create debug and db directories
RUN mkdir -p /app/debug /app/db && \
    chmod -R 755 /app

# Copy application files
COPY --from=build /app/target/*.jar app.jar

# Debug steps
RUN jar tvf /app/app.jar | grep h2 > /app/debug/jar_contents.txt || true && \
    cat /app/debug/jar_contents.txt || true && \
    jar tvf /app/app.jar | grep mediainfo > /app/debug/mediainfo_jar_contents.txt || true && \
    cat /app/debug/mediainfo_jar_contents.txt || true && \
    jar tvf /app/app.jar | grep MediaInfo > /app/debug/mediainfo_class_contents.txt || true && \
    cat /app/debug/mediainfo_class_contents.txt || true && \
    echo 'public class H2Test { public static void main(String[] args) { try { Class.forName("org.h2.Driver"); System.out.println("H2 Driver loaded successfully"); } catch (ClassNotFoundException e) { System.err.println("Failed to load H2 Driver: " + e.getMessage()); } } }' > /app/H2Test.java && \
    javac /app/H2Test.java && \
    java -cp /app:/app/app.jar -Djava.library.path=/usr/lib H2Test > /app/debug/h2_driver_test.txt 2>&1 || true && \
    cat /app/debug/h2_driver_test.txt || true && \
    echo 'public class MegaTest { public static void main(String[] args) { try { System.load("/usr/lib/libmega.so"); System.out.println("libmega.so loaded successfully"); } catch (UnsatisfiedLinkError e) { System.err.println("Failed to load libmega.so: " + e.getMessage()); } } }' > /app/MegaTest.java && \
    javac /app/MegaTest.java && \
    java -cp /app:/app/app.jar -Djava.library.path=/usr/lib MegaTest > /app/debug/mega_test.txt 2>&1 || true && \
    cat /app/debug/mega_test.txt || true && \
    echo 'public class MediaInfoTest { public static void main(String[] args) { try { Class.forName("MediaInfo"); System.out.println("MediaInfo class loaded successfully"); } catch (ClassNotFoundException e) { System.err.println("Failed to load MediaInfo class: " + e.getMessage()); } try { Class.forName("nz.mega.sdk.MediaInfo"); System.out.println("nz.mega.sdk.MediaInfo class loaded successfully"); } catch (ClassNotFoundException e) { System.err.println("Failed to load nz.mega.sdk.MediaInfo class: " + e.getMessage()); } System.out.println("Classpath: " + System.getProperty("java.class.path")); } }' > /app/MediaInfoTest.java && \
    javac -cp /app:/app/app.jar /app/MediaInfoTest.java && \
    java -cp /app:/app/app.jar -Djava.library.path=/usr/lib MediaInfoTest > /app/debug/mediainfo_test.txt 2>&1 || true && \
    cat /app/debug/mediainfo_test.txt || true && \
    ldd /usr/lib/libmega.so > /app/debug/ldd_output.txt && \
    cat /app/debug/ldd_output.txt && \
    ldd /usr/lib/libmediainfo.so > /app/debug/libmediainfo_ldd_output.txt && \
    cat /app/debug/libmediainfo_ldd_output.txt && \
    ls -l /usr/lib/libmediainfo.so* > /app/debug/libmediainfo_info.txt && \
    cat /app/debug/libmediainfo_info.txt || true && \
    ldconfig && \
    timeout 30s java -Djava.library.path=/usr/lib -Dspring.profiles.active=prod -jar /app/app.jar > /app/debug/java_test_output.txt 2>&1 || true && \
    cat /app/debug/java_test_output.txt || true

# Environment variables
ENV SPRING_DATASOURCE_URL=jdbc:h2:file:/app/db/cycling_power;AUTO_SERVER=TRUE
ENV SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_ADDRESS=0.0.0.0
ENV PORT=10000

# Expose the port
EXPOSE ${PORT}

# Run the app
ENTRYPOINT ["java", "-Djava.library.path=/usr/lib", "-Dserver.address=${SERVER_ADDRESS}", "-Dserver.port=${PORT}", "-jar", "app.jar"]

# Pokrenuti docker service -
# sudo dockerd
# Novi terminal, build pa run -
# sudo docker build -t app .
# sudo docker run -p 8080:8080 app
# ili run u servisa u pozadini
# sudo systemctl start docker ili sudo systemctl enable docker
# docker build -t cycling-app . && docker run --rm -it cycling-app ldd /usr/lib/libmega.so
# ili # Build the Docker image
# docker build -t cycling-app .
# Run the Docker container
# docker run -p 8080:10000 -e SPRING_PROFILES_ACTIVE=prod -e PORT=10000 -e MEGA_EMAIL=your_mega_email -e MEGA_PASSWORD=your_mega_password cycling-app
# Ctrl + D ili exit
# sudo systemctl stop docker.socket ili sudo systemctl disable docker.socket
# ciscenje docker trash-a
# Safely stop all running containers (only if they exist)
# sudo docker ps -q | xargs --no-run-if-empty sudo docker stop
# Then proceed with the rest of the cleanup:
# sudo docker system prune -a --volumes
# Test
# docker run --rm -p 10000:10000 -e SPRING_PROFILES_ACTIVE=prod -e PORT=10000 -e MEGA_EMAIL=java4now@gmail.com -e MEGA_PASSWORD=webfx2048 cycling-app