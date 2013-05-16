package org.cakemix.server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import org.cakemix.*;
import org.cakemix.Network.*;

public class ChatServer {

    // The Kryonet Server at the heart
    Server server;
    // The Visual log (move this)
    JList messageList;
    // welcome message send to all users on connect
    // Make this customisable in a settings file or menu or something
    ChatMessage welcome = new ChatMessage("Welcome to the Server,"
            + "type /help to get a list of commands.");
    // Help message
    // same as above
    String help = "/help - Print this help page." + '\n'
            + "/roll [num] - Roll [num] d10 dice" + '\n'
            + "/{me or em} <action> - Sends <action> as emotive text";

    /*
     * Most of the code here is just a copy pasta from the example
     * It differes on the slash commands, and a few others at the moment
     * Working on creating my own implementation
     */
    public ChatServer() throws IOException {
        server = new Server() {
            protected Connection newConnection() {
                // By providing our own connection implementation, we can store per
                // connection state without a connection ID to state look up.
                return new ChatConnection();
            }
        };

        // For consistency, the classes to be sent over the network are
        // registered by the same method for both the client and server.
        org.cakemix.Network.register(server);

        server.addListener(new Listener() {
            public void received(Connection c, Object object) {
                // We know all connections for this server are actually ChatConnections.
                ChatConnection connection = (ChatConnection) c;

                if (object instanceof RegisterName) {
                    // Ignore the object if a client has already registered a name. This is
                    // impossible with our client, but a hacker could send messages at any time.

                    //scratch above, use this to change the name
                    if (connection.name != null) {
                        // Ignore the object if the name is invalid.
                        String name = ((RegisterName) object).name;
                        if (name == null) {
                            return;
                        }
                        name = name.trim();
                        if (name.length() == 0) {
                            return;
                        }
                        return;
                    }
                    // Ignore the object if the name is invalid.
                    String name = ((RegisterName) object).name;
                    if (name == null) {
                        return;
                    }
                    name = name.trim();
                    if (name.length() == 0) {
                        return;
                    }
                    // Store the name on the connection.
                    connection.name = name;
                    // Send a "connected" message to everyone except the new client.
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.text = name + " connected.";
                    chatMessage.sendTo = ChatMessage.ANNOUNCE;
                    // log the message in the server list
                    DefaultListModel model = (DefaultListModel) messageList.getModel();
                    model.addElement(chatMessage.text);
                    messageList.ensureIndexIsVisible(model.size() - 1);
                    server.sendToAllExceptTCP(connection.getID(), chatMessage);
                    server.sendToTCP(connection.getID(), welcome);
                    // Send everyone a new list of connection names.
                    updateNames();
                    return;
                }

                if (object instanceof ChatMessage) {
                    // Ignore the object if a client tries to chat before registering a name.
                    if (connection.name == null) {
                        return;
                    }
                    ChatMessage chatMessage = (ChatMessage) object;
                    // Ignore the object if the chat message is invalid.
                    String message = chatMessage.text;
                    if (message == null) {
                        return;
                    }
                    message = message.trim();
                    if (message.length() == 0) {
                        return;
                    }
                    // Implement Regex

                    if (message.charAt(0) == '/') {

                        chatMessage = regex(message);
                        switch (chatMessage.sendTo) {
                            case (ChatMessage.ALL):
                                chatMessage.text = connection.name + ": "
                                        + chatMessage.text;
                                //log the message in the server list
                                DefaultListModel model = (DefaultListModel) messageList.getModel();
                                model.addElement(chatMessage.text);
                                messageList.ensureIndexIsVisible(model.size() - 1);
                                // send the message to clients
                                server.sendToAllTCP(chatMessage);
                                return;
                            case (ChatMessage.EMOTE):
                                chatMessage.text = connection.name + ""
                                        + chatMessage.text;
                                //log the message in the server list
                                model = (DefaultListModel) messageList.getModel();
                                model.addElement(chatMessage.text);
                                messageList.ensureIndexIsVisible(model.size() - 1);
                                // send the message to clients
                                server.sendToAllTCP(chatMessage);
                                return;
                            case (ChatMessage.SENDER):
                                // log the command output
                                model = (DefaultListModel) messageList.getModel();
                                model.addElement(connection.name + "::" + chatMessage.text);
                                // send output to user
                                server.sendToTCP(connection.getID(), chatMessage);
                                return;
                        }

                    }
                    // Prepend the connection's name and send to everyone.
                    chatMessage.text = connection.name + ": " + message;
                    //log the message in the server list
                    DefaultListModel model = (DefaultListModel) messageList.getModel();
                    model.addElement(chatMessage.text);
                    messageList.ensureIndexIsVisible(model.size() - 1);
                    // send the message to clients
                    server.sendToAllTCP(chatMessage);
                    return;
                }
            }

            public void disconnected(Connection c) {
                ChatConnection connection = (ChatConnection) c;
                if (connection.name != null) {
                    // Announce to everyone that someone (with a registered name) has left.
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.text = connection.name + " disconnected.";
                    chatMessage.sendTo = ChatMessage.ANNOUNCE;
                    // Log the message in the server list
                    DefaultListModel model = (DefaultListModel) messageList.getModel();
                    model.addElement(chatMessage.text);
                    messageList.ensureIndexIsVisible(model.size() - 1);
                    // Send to all clients
                    server.sendToAllTCP(chatMessage);
                    updateNames();
                }
            }
        });
        server.bind(org.cakemix.Network.port);
        server.start();

        // Open a window to provide an easy way to stop the server.
        JFrame frame = new JFrame("Chat Server");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent evt) {
                server.stop();
            }
        });
        frame.getContentPane().add(new JScrollPane(messageList = new JList()));
        messageList.setModel(new DefaultListModel());
        frame.setSize(320, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    void updateNames() {
        // Collect the names for each connection.
        Connection[] connections = server.getConnections();
        ArrayList names = new ArrayList(connections.length);
        ArrayList displays = new ArrayList(connections.length);
        for (int i = connections.length - 1; i >= 0; i--) {
            ChatConnection connection = (ChatConnection) connections[i];
            names.add(connection.name);
            displays.add(connection.name);
        }
        // Send the names to everyone.
        UpdateNames updateNames = new UpdateNames();
        updateNames.names = (String[]) names.toArray(new String[names.size()]);
        updateNames.displays = (String[]) displays.toArray(new String[displays.size()]);
        server.sendToAllTCP(updateNames);
    }

    protected ChatMessage regex(String message) {
        // create a new chatmessage to return
        ChatMessage chatMessage = new ChatMessage();

        // convert everything to lower case
        // remove the /
        // split the string into its componant parts
        String[] command = message.toLowerCase().substring(1).split(" ");
        if (command[0].equals("roll")) {
            // Since everyone needs to see rolls
            chatMessage.sendTo = ChatMessage.ALL;

            // check if they filled out the command right
            // and check that they are not tring to roll
            // an invalid number of dice
            if (command.length > 1 && Integer.parseInt(command[1]) > 0) {
                // roll the dice and and store the results
                int[] result = roll(Integer.parseInt(command[1]), 10);
                // set the success and rerolls
                int success = 0, reroll = 0;
                // start filling up the chat message
                chatMessage.text = "rolled " + result.length + " d10's" + "{ ";
                for (int i = 0; i < result.length; i++) {
                    chatMessage.text += result[i] + " ";
                    if (result[i] > 6) {
                        success++;

                    } else {
                        if (result[i] == 0) {
                            success++;
                            reroll++;
                        }
                    }
                }

                // output success and rerolls
                chatMessage.text += "}" +'\n' + "With "
                        + success + " Successes and " + reroll + " rerolls";
                return chatMessage;
            }

            // if they fuck up, give them command instructions
            chatMessage.sendTo = ChatMessage.SENDER;
            chatMessage.text = "/roll [num] - Roll [num] d10 dice";
        }
        // emote
        if (command[0].equals("me") || command[0].equals("em")) {
            // Since everyone needs to see emotes
            chatMessage.sendTo = ChatMessage.EMOTE;
            // cut out the command
            // and return the rest of the string
            chatMessage.text = message.substring(3);
            return chatMessage;
        }

        // return the user their helpful message
        chatMessage.sendTo = ChatMessage.SENDER;
        chatMessage.text = help;

        return chatMessage;
    }

    /*
     * return the result of a dice roll
     * @param dice number of dice to roll
     * @param type number of sides on the dice
     */
    private int[] roll(int dice, int type) {
        // create the result
        int[] result = new int[dice];

        //create the random number generator
        java.util.Random rand = new java.util.Random();
        //roll the dice
        for (int i = 0; i < result.length; i++) {

            result[i] = (int) (type * rand.nextFloat());
            System.out.println(result[i]);
        }

        //return the result
        return result;
    }

    // This holds per connection state.
    static class ChatConnection extends Connection {

        public String name;
    }
}
