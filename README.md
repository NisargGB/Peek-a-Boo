# ChatApplication
# Developed by --- Nisarg Bhatt (2017CS10354) --- Divyanshu Mandowara (2017CS10333)
Computer Networks Assignment-2: An end-to-end encrypted Java chat application.

# How to compile the project
javac -d [Project Home Directory]/bin [Project Home Directory]/src/*.java

# How to run Server
java [Project Home Directory]/bin/Server

#Gow to start a Client
java [Project Home Directory]/bin/Client [username] [Server IP] [mode]

NOTE:
	-- Username should be an alphanumeric string.
	-- mode = 1 for unencrypted chat mode.
	-- mode = 2 for encrypted chat mode.
	-- mode = 3 for encrypted chat mode with added check for message integrity.
