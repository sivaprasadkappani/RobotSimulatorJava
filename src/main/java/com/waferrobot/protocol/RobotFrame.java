package com.waferrobot.protocol;

/**
 * Immutable data class representing one parsed protocol frame.
 *
 * Frame format:
 *   <SOH>MESSAGE_TYPE|SEQUENCE_ID|PAYLOAD|CHECKSUM<CR><LF>   (with payload)
 *   <SOH>MESSAGE_TYPE|SEQUENCE_ID|CHECKSUM<CR><LF>           (without payload)
 */
public final class RobotFrame {

    private final String messageType;
    private final int    sequenceId;
    private final String payload;
    private final String hexChecksum;
    private final boolean isValid;

    public RobotFrame(String messageType, int sequenceId, String payload,
                      String hexChecksum, boolean isValid) {
        this.messageType = messageType;
        this.sequenceId  = sequenceId;
        this.payload     = payload;
        this.hexChecksum = hexChecksum;
        this.isValid     = isValid;
    }

    /** Protocol message type: CMD, ACK, NAK, EVT, or STAT. */
    public String getMessageType() { return messageType; }

    /** Request/response correlation number. */
    public int getSequenceId() { return sequenceId; }

    /** Command text or status/error code; may be empty. */
    public String getPayload() { return payload; }

    /** Received checksum text (2-char uppercase hex). */
    public String getHexChecksum() { return hexChecksum; }

    /** True when frame format and checksum pass validation. */
    public boolean isValid() { return isValid; }

    @Override
    public String toString() {
        return String.format("RobotFrame[type=%s, seq=%d, payload='%s', checksum=%s, valid=%b]",
                messageType, sequenceId, payload, hexChecksum, isValid);
    }
}
