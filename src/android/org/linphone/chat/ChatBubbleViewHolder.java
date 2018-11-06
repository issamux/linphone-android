/*
ChatBubbleViewjava
Copyright (C) 2017  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

package org.linphone.chat;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.ui.ContactAvatar;

public class ChatBubbleViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    public Context mContext;
    public ChatMessage message;

    public LinearLayout eventLayout;
    public TextView eventMessage;

    public LinearLayout securityEventLayout;
    public TextView securityEventMessage;

    public View rightAnchor;
    public RelativeLayout bubbleLayout;
    public RelativeLayout background;
    public RelativeLayout avatarLayout;

    public TextView timeText;
    public ImageView outgoingImdn;
    public TextView messageText;

    public CheckBox delete;
    private ClickListener mListener;

    public ChatBubbleViewHolder(Context context, View view, ClickListener listener) {
        this(view);
        mContext = context;
        mListener = listener;
        view.setOnClickListener(this);
    }

    public ChatBubbleViewHolder(View view) {
        super(view);
        eventLayout = view.findViewById(R.id.event);
        eventMessage = view.findViewById(R.id.event_text);

        securityEventLayout = view.findViewById(R.id.event);
        securityEventMessage = view.findViewById(R.id.event_text);

        rightAnchor = view.findViewById(R.id.rightAnchor);
        bubbleLayout = view.findViewById(R.id.bubble);
        background = view.findViewById(R.id.background);
        avatarLayout = view.findViewById(R.id.avatar_layout);

        timeText = view.findViewById(R.id.time);
        outgoingImdn = view.findViewById(R.id.imdn);
        messageText = view.findViewById(R.id.message);

        delete = view.findViewById(R.id.delete_message);
    }

    @Override
    public void onClick(View v) {
        if (mListener != null) {
            mListener.onItemClicked(getAdapterPosition());
        }
    }

    public interface ClickListener {
        void onItemClicked(int position);
    }

    public void bindMessage(ChatMessage message, LinphoneContact contact) {
        eventLayout.setVisibility(View.GONE);
        securityEventLayout.setVisibility(View.GONE);
        rightAnchor.setVisibility(View.VISIBLE);
        bubbleLayout.setVisibility(View.VISIBLE);
        messageText.setVisibility(View.GONE);
        timeText.setVisibility(View.VISIBLE);
        outgoingImdn.setVisibility(View.GONE);
        avatarLayout.setVisibility(View.GONE);

        ChatMessage.State status = message.getState();
        Address remoteSender = message.getFromAddress();
        String displayName;
        String time = LinphoneUtils.timestampToHumanDate(mContext, message.getTime(), R.string.messages_date_format);

        if (message.isOutgoing()) {
            if (status == ChatMessage.State.DeliveredToUser) {
                outgoingImdn.setVisibility(View.VISIBLE);
                outgoingImdn.setImageResource(R.drawable.imdn_received);
            } else if (status == ChatMessage.State.Displayed) {
                outgoingImdn.setVisibility(View.VISIBLE);
                outgoingImdn.setImageResource(R.drawable.imdn_read);
            } else if (status == ChatMessage.State.NotDelivered) {
                outgoingImdn.setVisibility(View.VISIBLE);
                outgoingImdn.setImageResource(R.drawable.imdn_error);
            } else if (status == ChatMessage.State.FileTransferError) {
                outgoingImdn.setVisibility(View.VISIBLE);
                outgoingImdn.setImageResource(R.drawable.imdn_error);
            }

            outgoingImdn.setVisibility(View.VISIBLE);
            timeText.setVisibility(View.VISIBLE);
            background.setBackgroundResource(R.drawable.chat_bubble_outgoing_full);
        } else {
            rightAnchor.setVisibility(View.GONE);
            avatarLayout.setVisibility(View.VISIBLE);
            background.setBackgroundResource(R.drawable.chat_bubble_incoming_full);
        }

        if (contact == null) {
            contact = ContactsManager.getInstance().findContactFromAddress(remoteSender);
        }
        if (contact != null) {
            if (contact.getFullName() != null) {
                displayName = contact.getFullName();
            } else {
                displayName = LinphoneUtils.getAddressDisplayName(remoteSender);
            }
            ContactAvatar.displayAvatar(contact, avatarLayout);
        } else {
            displayName = LinphoneUtils.getAddressDisplayName(remoteSender);
            ContactAvatar.displayAvatar(displayName, avatarLayout);
        }

        if (message.isOutgoing()) {
            timeText.setText(time);
        } else {
            timeText.setText(time + " - " + displayName);
        }

        if (message.hasTextContent()) {
            String msg = message.getTextContent();
            Spanned text = LinphoneUtils.getTextWithHttpLinks(msg);
            messageText.setText(text);
            messageText.setMovementMethod(LinkMovementMethod.getInstance());
            messageText.setVisibility(View.VISIBLE);
        }

        /*String externalBodyUrl = message.getExternalBodyUrl();
        Content fileTransferContent = message.getFileTransferInformation();

        boolean hasFile = message.getAppdata() != null;
        boolean hasFileTransfer = externalBodyUrl != null;
        for (Content c : message.getContents()) {
            if (c.isFile()) {
                hasFile = true;
            } else if (c.isFileTransfer()) {
                hasFileTransfer = true;
            }
        }
        if (hasFile) { // Something to display
            displayAttachedFile(message, holder);
        }

        if (hasFileTransfer) { // Incoming file transfer not yet downloaded
            fileName.setVisibility(View.VISIBLE);
            fileName.setText(fileTransferContent.getName());

            fileTransferLayout.setVisibility(View.VISIBLE);
            fileTransferProgressBar.setVisibility(View.GONE);
            if (message.isFileTransferInProgress()) { // Incoming file transfer in progress
                fileTransferAction.setVisibility(View.GONE);
            } else {
                fileTransferAction.setText(mContext.getString(R.string.accept));
                fileTransferAction.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mContext.getPackageManager().checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, mContext.getPackageName()) == PackageManager.PERMISSION_GRANTED) {
                            v.setEnabled(false);
                            String filename = message.getFileTransferInformation().getName();
                            File file = new File(LinphoneUtils.getStorageDirectory(mContext), filename);
                            int prefix = 1;
                            while (file.exists()) {
                                file = new File(LinphoneUtils.getStorageDirectory(mContext), prefix + "_" + filename);
                                Log.w("File with that name already exists, renamed to " + prefix + "_" + filename);
                                prefix += 1;
                            }
                            message.setListener(mListener);
                            message.setFileTransferFilepath(file.getPath());
                            message.downloadFile();

                        } else {
                            Log.w("WRITE_EXTERNAL_STORAGE permission not granted, won't be able to store the downloaded file");
                            LinphoneActivity.instance().checkAndRequestExternalStoragePermission();
                        }
                    }
                });
            }
        } else if (message.isFileTransferInProgress()) { // Outgoing file transfer in progress
            message.setListener(mListener); // add the listener for file upload progress display
            messageSendingInProgress.setVisibility(View.GONE);
            fileTransferLayout.setVisibility(View.VISIBLE);
            fileTransferAction.setText(mContext.getString(R.string.cancel));
            fileTransferAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    message.cancelFileTransfer();
                    notifyItemChanged(position);
                }
            });
        }*/
    }
}