package com.fsck.k9;

import com.fsck.k9.mail.Address;

/**
 * Stub for otherwise too complex intended usage of K-9 mailer Account class ...
 *
 * Created by Alexander S. Sidorov on 12/30/15.
 */
public class AccountStub
{
    public boolean isAnIdentity(Address[] replyToAddresses) {
        return true;
    }

    public boolean isAnIdentity(Address replyToAddresses) {
        return true;
    }

    public String getAlwaysBcc() {
        return "???";
    }

    public boolean isAlwaysShowCcBcc() {
        return false;
    }

    public String getOpenPgpProvider() {
        return null;
    }
}
