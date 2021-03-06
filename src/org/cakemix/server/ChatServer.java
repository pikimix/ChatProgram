package org.cakemix.server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import org.cakemix.*;
import org.cakemix.Network.*;

public class ChatServer {

    //<editor-fold desc="Vars" defaultstate="collapsed">
    // The Kryonet Server at the heart
    Server server;
    // The Visual log (move this)
    JList messageList;
    // welcome message send to all users on connect
    // Make this customisable in a settings file or menu or something
    ChatMessage welcome = new ChatMessage("Welcome to the Server, "
            + "type /help to get a list of commands.", ChatMessage.TYPE_ANNOUNCE);
    // Help message
    // same as above
    String help = "/help - Print this help page." + '\n'
            + "/roll [num] - Roll [num] d10 dice" + '\n'
            + "/{me or em} <action> - Sends <action> as emotive text" + '\n'
            + "/w{hisper} <name> <message> - Send <message> to <name>" + '\n'
            + "/alias <name> - Change Displayed Name to <name>";
    // GM Help
    // this gets ADDED to athe above in the case of a GM asking for help
    String gmHelp = "GM Commands" + '\n'
            + "/promote <name> - Promote <name> by one rank" + '\n'
            + "/demote <name> - Demote <name> by one rank" + '\n'
            + "/addgm <name> - Set <name> rank to GM" + '\n'
            + '\n' + "Standard Commands" + '\n';
    HashMap users;
    //Flag to know wether to output to console or gui list
    // Default to console only
    boolean console = true, gui = false;
    //</editor-fold>
    
    public ChatServer(boolean console) throws IOException{
        if (console){
            this.gui = false;
            this.console = true;
        }
        else{
            this.console = false;
            this.gui = true;
        }
        server = new Server() {

            protected Connection newConnection() {
                // By providing our own connection implementation, we can store per
                // connection state without a connection ID to state look up.
                return new ChatConnection();
            }
        };

        //create the hashmap to store user details
        users = new HashMap();

        // For consistency, the classes to be sent over the network are
        // registered by the same method for both the client and server.
        org.cakemix.Network.register(server);

        server.addListener(new Listener() {

            public void received(Connection c, Object object) {
                // We know all connections for this server are actually ChatConnections.
                ChatConnection connection = (ChatConnection) c;

                if (object instanceof RegisterName) {
                    registerName(connection, object);
                    return;
                }

                if (object instanceof ChatMessage) {
                    messageReceived(connection, object);
                    return;

                }
            }

            public void disconnected(Connection c) {
                ChatConnection connection = (ChatConnection) c;
                if (connection.name != null) {
                    // remove the name from the userlist
                    users.remove(connection.name);
                    // Announce to everyone that someone (with a registered name) has left.
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.text = connection.name + " disconnected.";
                    chatMessage.messageType = ChatMessage.TYPE_ANNOUNCE;
                    // Log the message in the server list
                    serverLog("(DISCONNECT)", connection);
                    // Send to all clients
                    server.sendToAllTCP(chatMessage);
                    updateNames();
                }
            }
        });
        server.bind(org.cakemix.Network.port);
        server.start();

        if (gui) {
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
    }
    
    /**
     * Most of the code here *was* just a copy pasta from the example It
     * differes on the slash commands, and a few others at the moment Working on
     * creating my own implementation
     */
    public ChatServer() throws IOException{
        this(true);
    }

    /**
     * Send an updated userlist to all connected
     */
    void updateNames() {
        // Collect the names for each connection.
        Connection[] connections = server.getConnections();
        String[] names = new String[connections.length];
        String[] displays = new String[connections.length];
        int[] ranks = new int[connections.length];
        // loop throught connections and get the names
        for (int i = connections.length - 1; i >= 0; i--) {
            ChatConnection connection = (ChatConnection) connections[i];
            // ake the first letter caps, cus it looks nice ;)
            names[i] = firstToUpper(connection.name);
            // also get their display name from the list
            // and give it a caps too :D
            displays[i] = firstToUpper(((String) users.get(connection.name)));
            // add the rank
            ranks[i] = connection.rank;
        }

        // Send the names to everyone.
        UpdateNames updateNames = new UpdateNames();
        updateNames.names = names;
        updateNames.displays = displays;
        updateNames.rank = ranks;


        server.sendToAllTCP(updateNames);
    }

    /**
     * Change The First Letter of a word to upper case
     *
     * @param arg word to edit
     * @return edited word
     */
    protected String firstToUpper(String arg) {
        return arg.substring(0, 1).toUpperCase()
                + arg.substring(1);
    }

    /**
     * GM Command Parsing
     *
     * @param connection connection of user in question
     * @param message the message they sent to the server (ie, com
     * @return
     */
    protected ChatMessage gmRegex(ChatConnection connection, String message) {

        // convert everything to lower case
        // remove the /
        // split the string into its componant parts
        String[] command = message.toLowerCase().substring(1).split(" ");
        // create an empty chat message to fill, incase its needed
        ChatMessage chatMessage = new ChatMessage();
        switch (command[0]) {
            case "promote":
                // check that the command has enough entries
                if (command.length > 2 && users.containsKey(command[1])) {
                    // loop through the connections
                    for (ChatConnection c : (ChatConnection[]) server.getConnections()) {
                        // if the connection name matches the rank
                        if (c.name == command[1]) {
                            // increase it by one
                            if (c.rank < ChatConnection.RANK_GM) {
                                c.rank++;
                                // return an announcement
                                chatMessage.messageType = ChatMessage.TYPE_ANNOUNCE;
                                chatMessage.text = connection.name + " promoted "
                                        + c.name;
                                return chatMessage;
                            }
                        }
                    }
                }
                chatMessage.messageType = ChatMessage.TYPE_SENDER;
                chatMessage.text = "/promote <name> - Promote <name> by one rank";
                return chatMessage;
            case "demote":
                // check that the command has enough entries
                if (command.length > 2 && users.containsKey(command[1])) {
                    // loop through the connections
                    for (ChatConnection c : (ChatConnection[]) server.getConnections()) {
                        // if the connection name matches the rank
                        if (c.name.equals(command[1])) {
                            // decrease it by one
                            if (c.rank > 0) {
                                c.rank--;

                                // return an announcement
                                chatMessage.messageType = ChatMessage.TYPE_ANNOUNCE;
                                chatMessage.text = connection.name + " demoted "
                                        + c.name;
                                return chatMessage;
                            }
                        }
                    }
                }
                chatMessage.messageType = ChatMessage.TYPE_SENDER;
                chatMessage.text = "/demote <name> - Demote <name> by one rank";
                return chatMessage;
            case "addgm":
                // check that the command has enough entries
                if (command.length > 2 && users.containsKey(command[1])) {
                    // loop through the connections
                    for (ChatConnection c : (ChatConnection[]) server.getConnections()) {
                        // if the connection name matches the rank
                        if (c.name == command[1]) {
                            // set it to GM
                            c.rank = ChatConnection.RANK_GM;
                            // return an announcement
                            chatMessage.messageType = ChatMessage.TYPE_ANNOUNCE;
                            chatMessage.text = connection.name + " made "
                                    + c.name + " a GM";
                            return chatMessage;
                        }
                    }
                }
                chatMessage.messageType = ChatMessage.TYPE_SENDER;
                chatMessage.text = "/addgm <name> - Set <name> rank to GM";
                return chatMessage;
            // scene descriptive text
            case "d":
            case "desc":
            case "description":
                chatMessage.messageType = ChatMessage.TYPE_DESCRIPTION;
                chatMessage.text = message.substring(command[0].length() + 1);
                return chatMessage;

        }

        // if its not a gm command, fallback to standard regex
        return regex(message);
    }

    /**
     * User command parsing
     *
     * @param message
     * @return
     */
    protected ChatMessage regex(String message) {
        // create a new chatmessage to return
        ChatMessage chatMessage = new ChatMessage();

        // convert everything to lower case
        // remove the /
        // split the string into its componant parts
        String[] command = message.toLowerCase().substring(1).split(" ");
        switch (command[0]) {

            case "roll":
                // Since everyone needs to see rolls
                chatMessage.messageType = ChatMessage.TYPE_ALL;

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
                        //for each roll, log its result
                        chatMessage.text += result[i] + " ";
                        if (result[i] > 6) {
                            // if it succeeds, log
                            success++;

                        } else {
                            if (result[i] == 0) {
                                // if its a reroll
                                // log a success and reroll
                                success++;
                                reroll++;
                            }
                        }
                    }
                    // output success and rerolls
                    chatMessage.text += "}" + '\n' + "With "
                            + success + " Successes and " + reroll + " rerolls";
                    return chatMessage;
                }

                // if they fuck up, give them command instructions
                chatMessage.messageType = ChatMessage.TYPE_SENDER;
                chatMessage.text = "/roll [num] - Roll [num] d10 dice";
                return chatMessage;

            // emote
            case "me":
            case "em":
                // Since everyone needs to see emotes as emotes
                // and not as messages
                chatMessage.messageType = ChatMessage.TYPE_EMOTE;
                // if the message is long enough
                if (message.length() > 4) {
                    // cut out the command
                    // and return the rest of the string
                    chatMessage.text = message.substring(4);
                } else {
                    //otherwise send them the help
                    chatMessage.text = "/{me or em} <action> - Sends <action> as emotive text";
                    chatMessage.messageType = ChatMessage.TYPE_SENDER;
                }
                return chatMessage;

            // personal message to player
            case "w":
            case "whisper":
                // check that there is a target user and a message
                if (command.length > 2) {
                    // find if theres a user matching the target
                    if (users.containsKey(command[1])) {
                        // since there is a user in the list
                        //set the message to a whisper
                        chatMessage.messageType = ChatMessage.TYPE_WHISPER;
                        // set the message target
                        chatMessage.target = command[1];

                        //set the message
                        chatMessage.text = message.substring(
                                command[0].length() + 2
                                + command[1].length() + 1);
                        return chatMessage;
                    } else {
                        // if there is no user, return the help message
                        return new ChatMessage(
                                "User Not Found <" + command[1] + ">" + '\n'
                                + "Current Users " + '\n'
                                + users.keySet().toString() + '\n'
                                + "Did you try and whisper an alias?",
                                ChatMessage.TYPE_SENDER);
                    }
                }
                return new ChatMessage(
                        "/w{hisper} <name> <message> - Send <message> to <name>"
                        + '\n' + "Currently not implemented",
                        ChatMessage.TYPE_SENDER);

            case "a":
            case "alias":
                // Check that there is a name to alias to
                if (command.length > 1) {
                    // Set message type
                    chatMessage.messageType = ChatMessage.TYPE_ALIAS;
                    // set the message (will be used to change name)
                    chatMessage.text = command[1];
                } else {
                    // cock up, send help
                    // seeing a pattren?
                    chatMessage.messageType = ChatMessage.TYPE_SENDER;
                    chatMessage.text = "/alias <name> - Change Displayed Name to <name>";
                }
                // return the message
                return chatMessage;

        }

        // return the user their helpful message
        chatMessage.messageType = ChatMessage.TYPE_SENDER;
        chatMessage.text = help;

        return chatMessage;
    }

    /**
     * return the result of a dice roll
     *
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
        }

        //return the result
        return result;


    }

    /**
     * Cut out the receiver handeling and chucked it down here
     *
     * Was getting a bit redicul-arse in size and was a nightmare to read
     * through Hopefully this will make it easyer, cus atleast it can be
     * collapsed
     */
    /**
     * what to do when a message is received
     */
    private void messageReceived(ChatConnection connection, Object object) {
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
        //<editor-fold desc="ChatMessage parsing">
        // Check for command inpt
        // Then pass the command input to the regex
        if (message.charAt(0) == '/') {
            //check if the connection has GM rights
            if (connection.rank == ChatConnection.RANK_GM) {
                // Run the gm regex
                chatMessage = gmRegex(connection, message);
                // if the chat message is null
                // command was handled fully in gmRegex
                // no further action needed
                if (chatMessage == null) {
                    return;
                }
            } else {
                // Run the standard user regex
                chatMessage = regex(message);
            }

            // Check that the user has rights to the message type
            // ie, they are above listener, or it is a whisper
            if (chatMessage.messageType == ChatMessage.TYPE_WHISPER
                    || connection.rank > ChatConnection.RANK_LISTENER) {
                switch (chatMessage.messageType) {

                    case (ChatMessage.TYPE_ALL):
                        chatMessage.text = firstToUpper(((String) users.get(connection.name))) + ": "
                                + chatMessage.text;
                        //log the message in the server list
                        serverLog("(STD) " + chatMessage.text, connection);
                        // send the message to clients
                        server.sendToAllTCP(chatMessage);
                        return;

                    case (ChatMessage.TYPE_EMOTE):
                        chatMessage.text = firstToUpper(((String) users.get(connection.name))) + " "
                                + chatMessage.text;

                        //log the message in the server list
                        serverLog("(EMOTE) " + chatMessage.text, connection);

                        // send the message to clients
                        server.sendToAllTCP(chatMessage);
                        return;
                    case (ChatMessage.TYPE_SENDER):
                        // Check if the GM is getting help
                        if (chatMessage.text.equals(help)
                                && connection.rank == ChatConnection.RANK_GM) {
                            // if they are, send them gm help instead
                            chatMessage.text = gmHelp + chatMessage.text;
                        }
                        // log the command output
                        serverLog("(COMMAND) " + chatMessage.text, connection);
                        // send output to user
                        server.sendToTCP(connection.getID(), chatMessage);

                        return;

                    case (ChatMessage.TYPE_ALIAS):
                        // change the display name
                        if (connection.name != null && connection.name.length() != 0
                                && chatMessage.text != null && chatMessage.text.length() != 0) {

                            if (!users.containsKey(connection.name)) {
                                users.put(connection.name, chatMessage.text);
                            } else {
                                // remove the old value
                                users.remove(connection.name);
                                // insert the new value
                                users.put(connection.name, chatMessage.text);
                            }

                            //log the name change
                            serverLog("(NAME CHANGE) "
                                    + firstToUpper(((String) users.get(connection.name))),
                                    connection);

                            // Update the name list on all clients
                            updateNames();
                            return;
                        }

                    case (ChatMessage.TYPE_DESCRIPTION):
                        //log the message in the server list
                        serverLog("(DESC) " + chatMessage.text, connection);

                        // send the message to clients
                        server.sendToAllTCP(chatMessage);
                        return;

                    case ChatMessage.TYPE_OFF_TOPIC:
                        //log the message in the server list
                        serverLog("(OFF TOPIC) " + chatMessage.text, connection);

                        chatMessage.text = firstToUpper(((String) users.get(connection.name))) + ": "
                                + chatMessage.text;

                        // send the message to clients
                        server.sendToAllTCP(chatMessage);
                        return;

                    case ChatMessage.TYPE_WHISPER:
                        // loop through the connections, find the matching user
                        // Send the user the message from here
                        // Send the confirmation (ie Sent To <user> :: <message>)
                        // as a target Sender return

                        // Check first if they are trying to whisper to them self
                        // if they do, send a little message to everyone else ;)
                        if (chatMessage.target.equals(connection.name)) {
                            server.sendToAllExceptTCP(connection.getID(),
                                    new ChatMessage(firstToUpper(((String) users.get(connection.name)))
                                    + " whispers to themselves",
                                    ChatMessage.TYPE_EMOTE));
                        }
                        // get the list of connections
                        Connection[] conn = server.getConnections();
                        // loop through the list of connections
                        for (int i = 0; i < conn.length; i++) {
                            ChatConnection tmp = (ChatConnection) conn[i];
                            if (tmp.name.equals(chatMessage.target)) {
                                // hold the message temporarily
                                String mess = chatMessage.text;

                                // create the message to send to target
                                chatMessage.text = "From " + firstToUpper(((String) users.get(connection.name)))
                                        + "(" + firstToUpper(connection.name)
                                        + "):: " + chatMessage.text;
                                // send the message to the target
                                server.sendToTCP(conn[i].getID(), chatMessage);

                                // set confirmation
                                chatMessage.text = "To " + firstToUpper(((String) users.get(tmp.name)))
                                        + "(" + firstToUpper(tmp.name) + "):: " + mess;
                                server.sendToTCP(connection.getID(), chatMessage);

                                //log the message and target
                                serverLog("(WHISPER "
                                        + firstToUpper(tmp.name) + ") "
                                        + mess, connection);

                                return;
                            }
                        }
                }
            }
        }
        //</editor-fold>
        // check that the message sender has rights to post messages
        if (connection.rank > ChatConnection.RANK_LISTENER) {
            //log the message in the server list
            serverLog(chatMessage.text, connection);

            // Prepend the connection's name and send to everyone.
            chatMessage.text = firstToUpper(((String) users.get(connection.name))) + ": " + message;

            // send the message to clients
            server.sendToAllTCP(chatMessage);
        } else // if not
        {
            chatMessage = new ChatMessage("You currently do not have rights to post messages." + '\n'
                    + "Please whisper the GM and ask for a promotion if you should be a Player",
                    ChatMessage.TYPE_SENDER);
            // Log the message
            serverLog("(ALERT) Listener tried to talk", connection);
            // send output to user
            server.sendToTCP(connection.getID(), chatMessage);
        }
    }

    private void serverLog(String message, ChatConnection connection) {
        if (gui) {
            DefaultListModel model = (DefaultListModel) messageList.getModel();
            model.addElement(
                    firstToUpper(connection.name) + " "
                    + message);
            messageList.ensureIndexIsVisible(model.size() - 1);
        }
        if (console) {
            System.out.println(
                    firstToUpper(connection.name) + " "
                    + message);

        }
    }

    // what to do if a RegisterName packet is received
    private void registerName(ChatConnection connection, Object object) {
        // get the values from the object
        String name = ((RegisterName) object).name;
        String display = ((RegisterName) object).displayName;
        // trim the values
        name = name.trim();
        display = display.trim();

        // Split the names by spaces and take the first entry
        name = name.split(" ")[0];
        display = display.split(" ")[0];

        // set everything to lower case (used for /w)
        name = name.toLowerCase();
        display = display.toLowerCase();

        // use this to change the name
        if (connection.name != null) {
            if (name != null && name.length() != 0
                    && display != null && display.length() != 0) {
                if (connection.name.equals(name)) {
                    if (!users.containsKey(name)) {
                        users.put(name, display);
                    } else {
                        // remove the old value
                        users.remove(name);
                        // insert the new value
                        users.put(name, display);
                    }
                }
                return;
            }
            return;
        }
        if (name == null && name.length() == 0) {
            return;
        }
        // Store the name on the connection.
        // Check that no one else is using the name
        if (!users.containsKey(name)) {
            // Store the connections name
            connection.name = name;
            // if this is the first connection, set it to gm
            if (users.values().isEmpty()) {
                connection.rank = ChatConnection.RANK_GM;
            }
            // check that the display name is valid
            if (display != null && display.length() != 0) {
                users.put(name, display);
            } else {
                users.put(name, name);
            }
        } else {
            // send a message to the user (ie, the object they sent)
            // this will flag on the client and get them to try a new name
            server.sendToTCP(connection.getID(), object);
            return;
        }

        // Send a "connected" message to everyone except the new client.
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.text = connection.name + " connected.";
        chatMessage.messageType = ChatMessage.TYPE_ANNOUNCE;

        // log the message in the server list
        serverLog("(CONNECTION)", connection);

        server.sendToAllExceptTCP(connection.getID(), chatMessage);
        server.sendToTCP(connection.getID(), welcome);
        // Send everyone a new list of connection names.
        updateNames();
    }
    /**
     * Dont think im using this anymoere...
     *
     * Okay, i completely stole this from Stack Overflow Thanks go to "Vitalii
     * Fedorenko"
     * http://stackoverflow.com/questions/1383797/java-hashmap-how-to-get-key-from-value
     */
//    public static <T, E> T getKeyByValue(Map<T, E> map, E value) {
//        for (Entry<T, E> entry : map.entrySet()) {
//            if (value.equals(entry.getValue())) {
//                return entry.getKey();
//            }
//        }
//        return null;
//    }
}
