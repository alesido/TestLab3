package org.alsi.android.testlab;

import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fsck.k9.AccountStub;
import com.fsck.k9.activity.RecipientMvpView;
import com.fsck.k9.activity.RecipientPresenter;
import com.fsck.k9.mail.Address;
import com.fsck.k9.ui.EolConvertingEditText;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.Message;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements RecipientMvpView.Controller
{
    GoogleAccountCredential mCredential;

    ProgressDialog mProgress;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { GmailScopes.GMAIL_LABELS, GmailScopes.GMAIL_COMPOSE };


    @Bind(R.id.subject)
    EditText subjectEditText;

    @Bind(R.id.message_content)
    EolConvertingEditText messageContentView;

    private RecipientPresenter recipientPresenter;

    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.message_compose2);

        ButterKnife.bind(this);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling G-mail API ...");

        // Initialize credentials and service object.
        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff())
                .setSelectedAccountName(settings.getString(PREF_ACCOUNT_NAME, null));

        RecipientMvpView recipientMvpView = new RecipientMvpView(this);
        recipientPresenter = new RecipientPresenter(this, recipientMvpView, new AccountStub());
    }

    public void onSendButtonClicked(View view) {
        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff())
                .setSelectedAccountName(settings.getString(PREF_ACCOUNT_NAME, null));
        new SendMailTask(credential).execute();
    }

    private Message sendEMail(Gmail gmailService, MimeMessage email) throws MessagingException, IOException
    {
        Message message = createMessageWithEmail(email);
        message = gmailService.users().messages().send("alsiessko@gmail.com", message).execute();
        return message;
    }

    private Message createMessageWithEmail(MimeMessage email) throws MessagingException, IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        email.writeTo(bytes);
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes.toByteArray());
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    public MimeMessage createEmail(String to, String from, String subject,
                                          String htmlText) throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(from));
        email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);
        email.setText(htmlText, "utf-8", "html");
        return email;
    }

    /** Create a MimeMessage using the parameters provided.
     *
     */
    public MimeMessage createEmailWithAttachment(String bodyText, String fileDir, String filename, String attachmentContentType) throws MessagingException, IOException
    {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        // from
        email.setFrom(new InternetAddress("alsiessko@gmail.com"));

        // to
        for (Address toAddress : recipientPresenter.getToAddresses()) {
            email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(toAddress.getAddress()));
        }
        for (Address ccAddress : recipientPresenter.getCcAddresses()) {
            email.addRecipient(javax.mail.Message.RecipientType.CC, new InternetAddress(ccAddress.getAddress()));
        }
        for (Address bccAddress : recipientPresenter.getBccAddresses()) {
            email.addRecipient(javax.mail.Message.RecipientType.CC, new InternetAddress(bccAddress.getAddress()));
        }

        // subject
        email.setSubject(subjectEditText.getText().toString());

        // body (html)
        email.setText(bodyText, "utf-8", "html");


        // attachment
        Multipart multipart = new MimeMultipart();

//        MimeBodyPart textBodyPart = new MimeBodyPart();
//        textBodyPart.setContent(bodyText, bodyContentType);
//        textBodyPart.setHeader("Content-Type", "text/plain; charset=\"UTF-8\"");
//        multipart.addBodyPart(mimeBodyPart);

        MimeBodyPart attachment = new MimeBodyPart();
        DataSource source = new FileDataSource(fileDir + filename);

        attachment.setDataHandler(new DataHandler(source));
        attachment.setFileName(filename);
        attachment.setHeader("Content-Type", attachmentContentType + "; name=\"" + filename + "\"");
        attachment.setHeader("Content-Transfer-Encoding", "base64");

        multipart.addBodyPart(attachment);

        email.setContent(multipart);

        return email;
    }

    private static final String EMAIL_ATTACHMENTS_ROOT_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() +
            File.separator + "Android" + File.separator + "data" + File.separator + BuildConfig.APPLICATION_ID + File.separator;

    private boolean ensureAttachmentFileExists(String inFileName)
    {
        String outFilePath = EMAIL_ATTACHMENTS_ROOT_PATH + inFileName;

        if ((new File(outFilePath)).exists())
            return true;

        InputStream is = null;
        FileOutputStream fos = null;

        try
        {
            File dir = new File(EMAIL_ATTACHMENTS_ROOT_PATH);
            if (!dir.exists())
                dir.mkdirs();

            is = getAssets().open(inFileName);
            fos = new FileOutputStream(outFilePath);

            byte buffer[] = new byte[1024];
            int length;
            while((length = is.read(buffer)) > 0) {
                fos.write(buffer,0,length);
            }
        }
        catch (IOException ex) {
            Log.e("MainActivity", "Cannot save email attachment!", ex);
            return false;
        }
        finally {
            try {if (is != null) is.close(); if (fos != null) fos.close(); } catch (Exception ex) {}
        }

        return true;
    }


    /**
     * Called whenever this activity is pushed to the foreground, such as after
     * a call to onCreate().
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (isGooglePlayServicesAvailable()) {
            refreshResults();
        } else {
            messageContentView.setText("Google Play Services required: " +
                    "after installing, close and relaunch this app.");
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    isGooglePlayServicesAvailable();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        mCredential.setSelectedAccountName(accountName);
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                    }
                } else if (resultCode == RESULT_CANCELED) {
                    messageContentView.setText("Account unspecified.");
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode != RESULT_OK) {
                    chooseAccount();
                }
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Attempt to get a set of data from the Gmail API to display. If the
     * email address isn't known yet, then call chooseAccount() method so the
     * user can pick an account.
     */
    private void refreshResults() {
        if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else {
            if (isDeviceOnline()) {
                new MakeRequestTask(mCredential).execute();
            } else {
                messageContentView.setText("No network connection available.");
            }
        }
    }

    /**
     * Starts an activity in Google Play Services so the user can pick an
     * account.
     */
    private void chooseAccount() {
        startActivityForResult(
                mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date. Will
     * launch an error dialog for the user to update Google Play Services if
     * possible.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        final int connectionStatusCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (GooglePlayServicesUtil.isUserRecoverableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
            return false;
        } else if (connectionStatusCode != ConnectionResult.SUCCESS ) {
            return false;
        }
        return true;
    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
                connectionStatusCode,
                MainActivity.this,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    @Override
    public void showContactPicker(int requestCode) {
        chooseAccount();
    }

    private class SendMailTask extends AsyncTask<Void, Void, Void>
    {
        private com.google.api.services.gmail.Gmail gMailService;

        public SendMailTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            gMailService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Android Test LAb")
                    .build();
        }

        @Override
        protected Void doInBackground(Void... params) {

            try {
//                MimeMessage email = createEmail("alsiessko@gmail.com", "me", "Test HTML mail",
//                        "<html>\n" +
//                                "\t<style type=\"text/css\">\n" +
//                                "\t\ttable.tableizer-table {\n" +
//                                "\t\t\tborder: 1px solid #000; font-family: Arial, Helvetica, sans-serif;\n" +
//                                "\t\t\tfont-size: 12px;\n" +
//                                "\t\t\t\n" +
//                                "\t\t\t\n" +
//                                "\t\t}\n" +
//                                "\t.tableizer-table td {\n" +
//                                "\t\tpadding: 4px;\n" +
//                                "\t\tmargin: 3px;\n" +
//                                "\t\tborder: 1px solid #000;\n" +
//                                "\t}\n" +
//                                "\t.tableizer-table th {\n" +
//                                "\t\tbackground-color: #CCC;\n" +
//                                "\t\tcolor: #000;\n" +
//                                "\t\tfont-weight: bold;\n" +
//                                "\t\ttext-align: left;\n" +
//                                "\t}\n" +
//                                "\t</style>\n" +
//                                "    <body>\n" +
//                                "\t\t<table width=100% class=\"tableizer-table\" cellpadding=\"4\"  cellspacing=\"0\">\n" +
//                                "            <tr class=\"tableizer-firstrow\"><th width=\"30%\">ABS Offices</th><th>ENQUIRIES: <a href=\"mailto:info@absatellite.com\" target=\"top\">info@absatellite.com</a></th></tr>\n" +
//                                "            <tr><td>BERMUDA</td><td>O' Hara House</br>3 Bermudiana Road</br>Hamilton</br>Bermuda</br><a href=\"tel:+441 295 7149\">Tel: +441 295 7149</a></td></tr>\n" +
//                                "\t\t\t<tr><td>USA</td><td>10220 River Road</br>Suite 306</br>Potomac, Maryland 20854</br><a href=\"tel:+1 301 605 7629\">Tel: +1 301 605 7629</a></td></tr>\n" +
//                                "\t\t\t<tr><td>UNITED ARAB EMIRATES</td><td>Unit 2107, Al Thuraya Tower 1</br>Dubai Media City, PO Box 502129</br>Dubai</br><a href=\"tel:+971 4 454 2677\">Tel: +971 4 454 2677</a></td></tr>\n" +
//                                "\t\t\t<tr><td>HONG KONG</td><td>28/F Henley Building</br>5 Queen's Road Central</br>Hong Kong</br><a href=\"tel:+852 3575 0000\">Tel: +852 3575 0000</a></td></tr>\t\t\t\n" +
//                                "\t\t\t<tr><td>PHILIPPINES</td><td>Broadband Broadcast Services</br>9/F Salcedo Towers</br>169 H.V. Dela Costa Street</br>Salcedo Village</br>Makati City, Manila</br><a href=\"tel:+ 632 8460 088\">Tel: + 632 8460 088</a></td></tr>\n" +
//                                "\t\t\t<tr><td>INDONESIA</td><td>PT BBS Indonesia</br>Wisma Dian Graha 3/F</br>Jl Rawa Gelam III No 8</br>Kawasan Industri</br>Pulo Gadung</br>Jakarta 13930</br><a href=\"tel:+6221 4682 4641\">Tel: +6221 4682 4641</a></td></tr>\n" +
//                                "\t\t\t<tr><td>SOUTH AFRICA</td><td>Suite B01b 1st Floor Block B Ambridge Office</br>Park Vrede Road, Bryanston</br>Johannesburg</br><a href=\"tel:+27 10 594 4621\">Tel: +27 10 594 4621</a></td></tr>\n" +
//                                "        </table>\n" +
//                                "    </body>\n" +
//                                "</html>");

                ensureAttachmentFileExists("mail_attachment_example.png");

                MimeMessage email = createEmailWithAttachment(
                        "<html>\n" +
                                "\t<style type=\"text/css\">\n" +
                                "\t\ttable.tableizer-table {\n" +
                                "\t\t\tborder: 1px solid #000; font-family: Arial, Helvetica, sans-serif;\n" +
                                "\t\t\tfont-size: 12px;\n" +
                                "\t\t\t\n" +
                                "\t\t\t\n" +
                                "\t\t}\n" +
                                "\t.tableizer-table td {\n" +
                                "\t\tpadding: 4px;\n" +
                                "\t\tmargin: 3px;\n" +
                                "\t\tborder: 1px solid #000;\n" +
                                "\t}\n" +
                                "\t.tableizer-table th {\n" +
                                "\t\tbackground-color: #CCC;\n" +
                                "\t\tcolor: #000;\n" +
                                "\t\tfont-weight: bold;\n" +
                                "\t\ttext-align: left;\n" +
                                "\t}\n" +
                                "\t</style>\n" +
                                "    <body>\n" +
                                "\t\t<table width=100% class=\"tableizer-table\" cellpadding=\"4\"  cellspacing=\"0\">\n" +
                                "            <tr class=\"tableizer-firstrow\"><th width=\"30%\">ABS Offices</th><th>ENQUIRIES: <a href=\"mailto:info@absatellite.com\" target=\"top\">info@absatellite.com</a></th></tr>\n" +
                                "            <tr><td>BERMUDA</td><td>O' Hara House</br>3 Bermudiana Road</br>Hamilton</br>Bermuda</br><a href=\"tel:+441 295 7149\">Tel: +441 295 7149</a></td></tr>\n" +
                                "\t\t\t<tr><td>USA</td><td>10220 River Road</br>Suite 306</br>Potomac, Maryland 20854</br><a href=\"tel:+1 301 605 7629\">Tel: +1 301 605 7629</a></td></tr>\n" +
                                "\t\t\t<tr><td>UNITED ARAB EMIRATES</td><td>Unit 2107, Al Thuraya Tower 1</br>Dubai Media City, PO Box 502129</br>Dubai</br><a href=\"tel:+971 4 454 2677\">Tel: +971 4 454 2677</a></td></tr>\n" +
                                "\t\t\t<tr><td>HONG KONG</td><td>28/F Henley Building</br>5 Queen's Road Central</br>Hong Kong</br><a href=\"tel:+852 3575 0000\">Tel: +852 3575 0000</a></td></tr>\t\t\t\n" +
                                "\t\t\t<tr><td>PHILIPPINES</td><td>Broadband Broadcast Services</br>9/F Salcedo Towers</br>169 H.V. Dela Costa Street</br>Salcedo Village</br>Makati City, Manila</br><a href=\"tel:+ 632 8460 088\">Tel: + 632 8460 088</a></td></tr>\n" +
                                "\t\t\t<tr><td>INDONESIA</td><td>PT BBS Indonesia</br>Wisma Dian Graha 3/F</br>Jl Rawa Gelam III No 8</br>Kawasan Industri</br>Pulo Gadung</br>Jakarta 13930</br><a href=\"tel:+6221 4682 4641\">Tel: +6221 4682 4641</a></td></tr>\n" +
                                "\t\t\t<tr><td>SOUTH AFRICA</td><td>Suite B01b 1st Floor Block B Ambridge Office</br>Park Vrede Road, Bryanston</br>Johannesburg</br><a href=\"tel:+27 10 594 4621\">Tel: +27 10 594 4621</a></td></tr>\n" +
                                "        </table>\n" +
                                "    </body>\n" +
                                "</html>",
                        EMAIL_ATTACHMENTS_ROOT_PATH, "mail_attachment_example.png", "image/png");

                sendEMail(gMailService, email);
            }
            catch (MessagingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }



    /**
     * An asynchronous task that handles the Gmail API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        private com.google.api.services.gmail.Gmail mService = null;
        private Exception mLastError = null;

        public MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Android Test LAb")
                    .build();
        }

        /**
         * Background task to call Gmail API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of Gmail labels attached to the specified account.
         * @return List of Strings labels.
         * @throws IOException
         */
        private List<String> getDataFromApi() throws IOException {
            // Get the labels in the user's account.
            String user = "me";
            List<String> labels = new ArrayList<String>();
            ListLabelsResponse listResponse =
                    mService.users().labels().list(user).execute();
            for (Label label : listResponse.getLabels()) {
                labels.add(label.getName());
            }
            return labels;
        }


        @Override
        protected void onPreExecute() {
            messageContentView.setText("");
            mProgress.show();
        }

        @Override
        protected void onPostExecute(List<String> output) {
            mProgress.hide();
            if (output == null || output.size() == 0) {
                messageContentView.setText("No results returned.");
            } else {
                output.add(0, "Data retrieved using the Gmail API:");
                messageContentView.setText(TextUtils.join("\n", output));
            }
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    messageContentView.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                messageContentView.setText("Request cancelled.");
            }
        }

//        public void execute() {
//        }
    }
}
