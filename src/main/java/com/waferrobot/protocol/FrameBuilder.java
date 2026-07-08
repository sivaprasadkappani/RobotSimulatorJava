package com.waferrobot.protocol;

/**
 * Builds complete outbound protocol frames.
 *
 * Frame format with payload:
 *   <SOH>MESSAGE_TYPE|SEQUENCE_ID|PAYLOAD|CHECKSUM<CR><LF>
 *
 * Frame format without payload:
 *   <SOH>MESSAGE_TYPE|SEQUENCE_ID|CHECKSUM<CR><LF>
 */
public class FrameBuilder {

    private static final char SOH  = 0x01;
    private static final String CRLF = "\r\n";
    private static final char SEPARATOR = '|';

    private final ChecksumCalculator checksumCalculator = new ChecksumCalculator();

    /**
     * Builds a complete protocol frame with a payload.
     *
     * @param messageType protocol message type, e.g. "CMD", "ACK", "NAK"
     * @param sequenceId  sequence number to place in the frame
     * @param payload     optional payload; pass empty string for no payload
     * @return complete frame string including SOH, checksum, and CRLF
     */
    public String build(String messageType, int sequenceId, String payload) {
        String coreData;
        if (payload == null || payload.isEmpty()) {
            coreData = messageType + SEPARATOR + sequenceId;
        } else {
            coreData = messageType + SEPARATOR + sequenceId + SEPARATOR + payload;
        }

        String checksum = checksumCalculator.calculate(coreData);
        return SOH + coreData + SEPARATOR + checksum + CRLF;
    }

    /**
     * Builds a complete protocol frame without a payload.
     *
     * @param messageType protocol message type
     * @param sequenceId  sequence number
     * @return complete frame string
     */
    public String build(String messageType, int sequenceId) {
        return build(messageType, sequenceId, "");
    }
}
