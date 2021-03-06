/*
 * Tabletop RPG Chat Client
 * Using Kryonet Networking Library
 * Some code based on chat example from Kryonet site
 * http://code.google.com/p/kryonet/
 */
package org.cakemix.client;

import org.cakemix.client.settings.MessageAttributes;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.cakemix.Network;
import org.cakemix.Network.ChatConnection;
import org.cakemix.Network.ChatMessage;
import org.cakemix.Network.UpdateNames;
import org.cakemix.characterSheets.Changeling;
import org.cakemix.client.settings.ChatSettings;
import org.cakemix.client.settings.ConfigIO;
import org.cakemix.client.settings.StylePickerFrame;
import sun.swing.SwingAccessor;

/**
 *
 * @author cakemix
 */
public class ClientFrame extends JFrame implements ActionListener,
        ItemListener {

    // Where messages are displayed
    JTextPane messageList;
    // Document that holds the messages
    StyledDocument doc;
    // Config file reader
    ConfigIO configIO = new ConfigIO();
    // Holds all the current chat settings
    ChatSettings settings;
    //Currently logged in users
    JList userList;
    // User inputted text
    JTextField sendText;
    // Scrollpane that holds userlist
    // is here so its visibillity can be changed
    JScrollPane userPane;
    //network Client
    ChatClient client;

    public ClientFrame() {
        super("Chat Client");
        // set default close operation
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLocation(100, 100);

        // before anything else occurs, load the settings file
        loadSettings();

        //create a network client
        client = new ChatClient();

        // Build the frames menu
        buildMenu();

        // build the form elements
        buildUI(getContentPane());

        // Pack the frame
        pack();

        // Show the frame
        setVisible(true);
    }

    /**
     * Override dispose so that it disconnects the client first
     */
    @Override
    public void dispose() {
        //disconnect the chat connection
        client.disconnect();
        try {
            configIO.saveSettings(settings);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // call dispose from above
        super.dispose();
        System.exit(0);
    }

    private void loadSettings() {
        settings = new ChatSettings();
        try {
            configIO.loadSettings(new FileReader("chatConfig.ini"));
            settings.setStyle(configIO);
        } catch (IOException ex) {
            Logger.getLogger(ClientFrame.class.getName()).log(Level.WARNING,
                    "ERROR: Loading config file, Reverting to defaults", ex);

            settings = ChatSettings.setToDefalts();
        }
    }

    /**
     * Create the form
     */
    private void buildUI(Container contentPane) {

        // Create the layout for the form
        GroupLayout layout = new GroupLayout(contentPane);
        // Set auto-create gaps, makes it look neater
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        // tell the panel to use the layout
        contentPane.setLayout(layout);
        // create the panes
        // messages
        JScrollPane messagePane = new JScrollPane(messageList = new JTextPane());
        // Disable editing displayed mesages
        messageList.setEditable(false);
        // Create the document to hold the messages
        doc = messageList.getStyledDocument();

        // user list
        userPane = new JScrollPane(userList = new JList());

        //set list porperties
        userList.setModel(new DefaultListModel());

        //create input controls
        sendText = new JTextField();
        final JButton sendButton = new JButton("Send");

        // Give the input controls actions
        sendButton.addActionListener(this);
        //Send text gets a special one
        // tells it to act like the send button
        sendText.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendButton.doClick();
            }
        });

        // after all items are created
        // apply the styles to them
        applySettings();

        // create the group
        layout.setHonorsVisibility(true);
        layout.setHorizontalGroup(layout.createParallelGroup()
                //main focus (left list && right chat)
                .addGroup(layout.createSequentialGroup()
                .addComponent(userPane, 64, 128, 192)
                .addComponent(messagePane, 64, 384, Short.MAX_VALUE))
                //input at the bottom
                .addGroup(layout.createSequentialGroup()
                .addComponent(sendText)
                .addComponent(sendButton)));
        layout.setVerticalGroup(layout.createSequentialGroup()
                //main focus (left list && right chat)
                .addGroup(layout.createParallelGroup()
                .addComponent(messagePane, 64, 256, Short.MAX_VALUE)
                .addComponent(userPane))
                //input at the bottom
                .addGroup(layout.createParallelGroup(
                GroupLayout.Alignment.TRAILING, false)
                .addComponent(sendText)
                .addComponent(sendButton)));

    }

    /**
     * Create the menu bar
     */
    private void buildMenu() {
        // Create the root bar of the menu
        JMenuBar menu = new JMenuBar();

        // Create the "File" menu
        JMenu file = new JMenu("File");
        // Create the "View" menu
        JMenu tools = new JMenu("Tools");
        // Create the "Help" menu
        JMenu help = new JMenu("Help");

        // Add the menus to the root menubar
        menu.add(file);
        menu.add(tools);
        menu.add(help);

        // Populate the menus
        // File Menu
        // Create a connect to menu item
        JMenuItem menuItem = new JMenuItem("Connect");
        // Add Action Listener to the item
        menuItem.addActionListener(this);
        // Add the menu item to the menu (File here)
        file.add(menuItem);

        //Add a menu separator before Exit item
        file.addSeparator();
        // Create an Exit menu item
        menuItem = new JMenuItem("Exit");
        // Add Action Listener to the item
        menuItem.addActionListener(this);
        // Add the menu item to the menu (File here)
        file.add(menuItem);

        // Tools Menu
        // Setup text colors
        menuItem = new JMenuItem("Set Colours");
        menuItem.addActionListener(this);
        tools.add(menuItem);

        // Character sheet
        menuItem = new JMenuItem("Changeling Sheet");
        menuItem.addActionListener(this);
        tools.add(menuItem);

        // Checkbox for show/hiding user list
        JCheckBoxMenuItem menuChk = new JCheckBoxMenuItem("Show User List");
        // Give the item a name, used to distingish between them
        menuChk.setName("Users");
        // default to checked, as userlist is shown by default
        menuChk.setSelected(true);
        // Add listener after the change, otherwise it errors due to the
        // full frame not being ready
        menuChk.addItemListener(this);

        // add to view menu
        tools.add(menuChk);

        // Set this form to use this menu by default
        setJMenuBar(menu);

    }

    /**
     * Update the userlist
     */
    public void setNames(final UpdateNames updateNames) {
        // This listener is run on the client's update thread,
        // which was started by client.start().
        // We must be careful to only interact with Swing
        // components on the Swing event thread.
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                DefaultListModel model = (DefaultListModel) userList.getModel();
                model.removeAllElements();
                for (int i = 0; i < updateNames.names.length; i++) {
                    String rank = null;
                    switch (updateNames.rank[i]) {
                        case ChatConnection.RANK_GM:
                            rank = "GM";
                            break;
                        case ChatConnection.RANK_LISTENER:
                            rank = "Creeper";
                            break;
                        case ChatConnection.RANK_PLAYER:
                            rank = "Player";
                            break;
                    }
                    if (updateNames.displays[i] != null) {
                        model.addElement(updateNames.displays[i] + "("
                                + updateNames.names[i] + ") - " + rank);
                    } else {
                        model.addElement(updateNames.names[i] + "("
                                + updateNames.names[i] + ") - " + rank);
                    }
                }
            }
        });
    }

    public void setSettings(ChatSettings settings) {
        // update the settings
        this.settings = settings;
        applySettings();

    }

    private void applySettings() {

        // Update te background color of the message List
        messageList.setBackground(settings.getBackgroundColor());
        // updatethe background colour of the user list
        userList.setBackground(settings.getBackgroundColor());
        // And set its text color to match the standard message
        userList.setForeground(
                settings.getMessageAttributes()[ChatMessage.TYPE_ALL].getColor());
        // and do the same for the send text
        sendText.setBackground(settings.getBackgroundColor());
        // possibly change the send text as some point to reflect the message type
        // in an item listener
        sendText.setForeground(
                settings.getMessageAttributes()[ChatMessage.TYPE_ALL].getColor());
    }

    /**
     * Add a new message to the list
     */
    public void addMessage(final ChatMessage message) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    // append a new line to the end of the message
                    message.text += '\n';
                    // Get the current document length
                    // Used for styling
                    int curLength = doc.getLength();
                    //insert the message to the doccument
                    doc.insertString(doc.getLength(), message.text, null);
                    // add the style for the line
                    doc.setCharacterAttributes( // explanation
                            // start point (ie, before new message)
                            curLength,
                            // length of styling (current length - start point)
                            doc.getLength() - curLength,
                            // message style taken from style array
                            settings.getMessageAttributes()[message.messageType].attributes,
                            // replace any formatting that exists
                            // for the line±
                            true);
                    return;

                } catch (BadLocationException ex) {
                    Logger.getLogger(ChatClient.class.getName()).log(
                            Level.SEVERE, null, ex);
                }
                messageList.setDocument(doc);
                messageList.setCaretPosition(doc.getLength());
            }
        });
    }

    /**
     * Get text from user input
     */
    public String getSendText() {
        return sendText.getText();
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        // Decide what to do based on the Action Command
        switch (ae.getActionCommand()) {

            // Menu Actions
            // Exit button in File Menu
            case "Exit":
                this.dispose();
                return;
            // Connect button in File Menu
            case "Connect":
                // Dispose of the current client
                client.disconnect();
                //setup a new one
                client = new ChatClient(Network.getConnectionDetails(), this);
                return;

            case "Set Colours":
                try {
                    new StylePickerFrame(settings, this);
                } catch (BadLocationException ex) {
                    Logger.getLogger(ClientFrame.class.getName()).log(
                            Level.SEVERE, null,
                            ex);
                }
                return;

            case "Changeling Sheet":
                new Changeling();
                break;

            //Main UI
            // Send button/ sendText action
            case "Send":
                // Check that there is text to send
                if (getSendText().length() == 0) {
                    return;
                }
                // Create the runnable object that sends the text
                // in another thread
                Runnable listener = new Runnable() {
                    public void run() {
                        // create the message
                        Network.ChatMessage chatMessage = new Network.ChatMessage();
                        chatMessage.text = getSendText();
                        // send the message through the client
                        client.getClient().sendTCP(chatMessage);
                    }
                };
                // run the code above
                listener.run();
                // empty the send text and give it focus
                sendText.setText("");
                sendText.requestFocus();
                return;

            // If no action is assigned to the command
            // Throw an error with the command name
            default:
                throw new UnsupportedOperationException(
                        "Not supported yet." + '\n'
                        + "Client Frame Action Command <" + ae.getActionCommand() + ">");
        }
    }

    @Override
    public void itemStateChanged(ItemEvent ie) {
        // check what type of object is sent via ie.getItem()
        // then do as above based on output of that command
        Object object = ie.getItem();
        if (object instanceof JCheckBoxMenuItem) {
            JCheckBoxMenuItem menuChk = (JCheckBoxMenuItem) object;
            switch (menuChk.getName()) {
                case "Users":
                    // set the users panel to be visible only if the checkbox
                    // is checked, and hide it otherwise
                    userPane.setVisible(menuChk.isSelected());
                    // validate the rest of the componants
                    // that is juggle and resize everything so it fills the form
                    // and looks nice ;D (fuck off pack! :P *ttthhhhhssssspppp*)
                    // this.pack(); <<< kinda looks sad now don't you think?
                    this.validate();
                    return;
            }
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
