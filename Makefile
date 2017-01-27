TARGETDIR=target/docker
TARGET=stage
IMAGE_NAME=csync

all : sbt_compile;

sbt_% :
	../sbt $*

% : sbt_%;

image:
	@ echo ""
	@ echo "Creating all in one docker image (will take a few minutes). *** Ctrl-C NOW to abort *** "
	@ sleep 7
	@ echo "Running sbt clean docker:stage"
	./npmgulp.sh
	@ sbt clean clean-files docker:stage
	@ docker build --no-cache=true -t ${IMAGE_NAME} ${TARGETDIR}/${TARGET}