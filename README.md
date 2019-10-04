# Peek-a-Boo - A PC command line based end-to-end encrypted chat application
Developed by --- Nisarg Bhatt --- Divyanshu Mandowara
Computer Networks Assignment-2: An end-to-end encrypted Java chat application.

# Usage
Compile the project
'javac -d [Project Home Directory]/bin [Project Home Directory]/src/*.java'

Setup Server
'cd [Project Home Directory]/bin'
'java Server'

Start a Client
'cd [Project Home Directory]/bin'
'java Client [username] [Server IP] [mode]'

NOTE:
	-- Username should be an alphanumeric string.
	-- mode = 1 for unencrypted chat mode.
	-- mode = 2 for encrypted chat mode. (RSA Algorithm)
	-- mode = 3 for encrypted chat mode with added check for message integrity through message signatures. (SHA-256)
