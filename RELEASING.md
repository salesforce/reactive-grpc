#Steps
1. Setup a pgp key
2. [Set up settings.xml](http://central.sonatype.org/pages/apache-maven.html)
3. Make sure pom version is right (no -SNAPSHOT)
4. `mvn clean deploy -Dmaven.test.skip -P public-release`
5. [Release the deployment from Nexus](http://central.sonatype.org/pages/releasing-the-deployment.html)

#Known issues
* Inappropriate ioctl for device
   ```bash
   GPG_TTY=$(tty)
   export GPG_TTY
   ```