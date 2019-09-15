# ChatApplication
# Developed by --- Nisarg Bhatt (2017CS10354) --- Divyanshu Mandowara (2017CS10333)
Computer Networks Assignment-2: An end-to-end encrypted Java chat application.

# Compile the project
javac -d [Project Home Directory]/bin [Project Home Directory]/src/*.java

# Setup Server (Ubuntu)
java [Project Home Directory]/bin/Server

# Start a Client (Ubuntu)
java [Project Home Directory]/bin/Client [username] [Server IP] [mode]

# Setup Server (Windows)
cd [Project Home Directory]/bin
java Server

# Start a Client (Windows)
cd [Project Home Directory]/bin
java Client [username] [Server IP] [mode]

NOTE:
	-- Username should be an alphanumeric string.
	-- mode = 1 for unencrypted chat mode.
	-- mode = 2 for encrypted chat mode. (RSA Algorithm)
	-- mode = 3 for encrypted chat mode with added check for message integrity through message sgnatures. (SHA-256)
