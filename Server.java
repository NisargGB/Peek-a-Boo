import java.util.*;
import javax.lang.model.util.ElementScanner6;
import java.io.*;
import java.net.*;


class ClientThread implements Runnable
{
    boolean recieving;
    boolean sending;
    Socket clientSocket;
    BufferedReader inFromClient;
    DataOutputStream outToClient;
    Hashtable<String,Socket> recieverSocketsMap;
    Hashtable<String,Socket> senderSocketsMap;

    public ClientThread(Socket clientSocketIn, Hashtable<String,Socket> recieversIn, Hashtable<String,Socket> sendersIn)
    {
        recieving = false;
        sending = false;
        clientSocket = clientSocketIn;
        recieverSocketsMap = recieversIn;
        senderSocketsMap = sendersIn;
        try
        {
            inFromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            outToClient = new DataOutputStream(clientSocket.getOutputStream());
        }
        catch(IOException e)
        {
            System.out.println(e);
        }
    }

    @Override
    public void run()
    {
        try
        {
            boolean registeredClient = false;
            

            while(!registeredClient)
            {
                String message = inFromClient.readLine();
                // System.out.println("Readline called for register command: " + message);
                String[] messageSplit = message.split(" ");

                if(messageSplit[0].equals("REGISTER") && messageSplit[1].equals("TOSEND") && messageSplit.length==3)
                {
                    String username = "";                       //Extract the username
                    if(messageSplit[2].split("\n").length==1)
                        username = messageSplit[2].split("\n")[0];
                    
                    if(this.isValidUsername(username))          //Only if username is valid
                    {
                        senderSocketsMap.put(username, clientSocket);
                        registeredClient= true;
                        outToClient.writeBytes("REGISTERED TOSEND " + username + "\n\n");
                        inFromClient.readLine();                //Ignoring the extra \n after the registration message

                        while(true)                             //Accepting messages
                        {
                            String newMessage = inFromClient.readLine();
                            // System.out.println("First readline awaiting the SEND message: " + newMessage);
                            String targetUser = newMessage.split(" ")[1].split("\n")[0];
                            newMessage = inFromClient.readLine();
                            // System.out.println("Readline accepting the content length message: " + newMessage);
                            inFromClient.readLine();                    //Ignoring the extra \n
                            
                            int contentLength = Integer.parseInt(newMessage.split(" ")[1]);
                            // char[] content = new char[contentLength];
                            // int flag = inFromClient.read(content, 0, contentLength);
                            // String contentString = new String(content);
                            // System.out.println("here1");
                            String contentString = inFromClient.readLine();
                            // System.out.println("here2");
                            // inFromClient.readLine();                        //Ignoring the \n after the content line
                            // System.out.print("The content char array read: ");
                            // for(char c : contentString)
                            //     System.out.print(c + " ");
                            // System.out.println("flag: " + flag);
                            String packetToBeSent =  "FORWARD " + username + "\n" + newMessage + "\n\n" + contentString + "\n";
                            // System.out.println("The packet server prepares from the message it recieves: " + packetToBeSent);
                            // System.out.println(contentString);

                            Socket targetSocket = recieverSocketsMap.get(targetUser);
                            if(targetSocket==null)
                            {
                                outToClient.writeBytes("ERROR 102 Unable to send\n");
                                continue;
                            }

                            DataOutputStream targetStream = new DataOutputStream(targetSocket.getOutputStream());
                            targetStream.writeBytes(packetToBeSent);

                            outToClient.writeBytes("SENT " + targetUser + "\n\n");
                        }
                    }
                    else
                    {
                        this.sendUsernameError();
                    }
                }
                else if(messageSplit[0].equals("REGISTER") && messageSplit[1].equals("TORECV") && messageSplit.length==3)
                {
                    String username = "";
                    if(messageSplit[2].split("\n").length==1)
                        username = messageSplit[2].split("\n")[0];
                    
                    inFromClient.readLine();    //Ignoring the extra \n after REGISTER line
                    recieverSocketsMap.put(username, clientSocket);
                    registeredClient = true;
                    outToClient.writeBytes("REGISTERED TORECV " + username + "\n\n");
                }
                else if(messageSplit.length==3)
                {
                    outToClient.writeBytes("ERROR 101 No user registered \n\n");
                }
                else
                {
                    this.sendUsernameError();
                }
            }
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }


    public boolean isValidUsername(String username)
    {
        return username.matches("[a-zA-Z0-9]+");
    }

    public void sendUsernameError() throws IOException
    {
        outToClient.writeBytes("ERROR 100 Malformed username\n\n");
    }
}



public class Server
{
    static Hashtable<String,Socket> recieverSockets = new Hashtable<String,Socket>();
    static Hashtable<String,Socket> senderSockets = new Hashtable<String,Socket>();

    public static void main(String args[]) throws Exception
    {
        //Command line inputs:

        ServerSocket serverSocket = new ServerSocket(1234);
        ArrayList<Thread> threads = new ArrayList<Thread>();

        while(true)
        {
            Socket connection = serverSocket.accept();
            Thread t = new Thread(new ClientThread(connection, recieverSockets, senderSockets));
            threads.add(t);
            t.start();
        }
    }

}