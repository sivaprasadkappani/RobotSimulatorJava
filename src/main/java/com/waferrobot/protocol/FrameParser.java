package com.waferrobot.protocol;

/**
 * Parses a raw protocol frame string into a RobotFrame.
 *
 * Supports both frame formats:
 *   4-field: <SOH>TYPE|SEQ_ID|PAYLOAD|CHECKSUM<CR><LF>
 *   3-field: <SOH>TYPE|SEQ_ID|CHECKSUM<CR><LF>
 */
public class FrameParser {

    private static final char SOH = 0x01;
    private final ChecksumCalculator checksumCalculator = new ChecksumCalculator();

    /**
     * Parses a raw frame string into a RobotFrame.
     * Sets isValid=false when the frame is malformed or the checksum fails.
     *
     * @param rawFrame complete received frame including SOH and CRLF
     * @return populated RobotFrame; isValid indicates parse success
     */
    public RobotFrame parse(String rawFrame) {
        if (rawFrame == null || rawFrame.isEmpty()) {
            System.out.println("[FrameParser] ERROR: Received empty or null frame.");
            return invalid();
        }

        // Strip SOH prefix
        String working = rawFrame;
        if (working.charAt(0) == SOH) {
            working = working.substring(1);
        }

        // Strip trailing CR and/or LF
        working = working.stripTrailing();
        while (working.endsWith("\r") || working.endsWith("\n")) {
            working = working.substring(0, working.length() - 1);
        }

        String[] parts = working.split("\\|", -1);

        if (parts.length == 3) {
            // No-payload frame: TYPE|SEQ_ID|CHECKSUM
            return parseFields(parts[0], parts[1], "", parts[2]);
        } else if (parts.length == 4) {
            // Payload frame: TYPE|SEQ_ID|PAYLOAD|CHECKSUM
            return parseFields(parts[0], parts[1], parts[2], parts[3]);
        } else {
            System.out.println("[FrameParser] ERROR: Unexpected field count " + parts.length
                    + " in frame: " + rawFrame);
            return invalid();
        }
    }

    private RobotFrame parseFields(String messageType, String seqStr,
                                   String payload, String receivedChecksum) {
        int sequenceId;
        try {
            sequenceId = Integer.parseInt(seqStr.trim());
        } catch (NumberFormatException e) {
            System.out.println("[FrameParser] ERROR: Invalid sequence ID '" + seqStr + "'.");
            return invalid();
        }

        // Rebuild core data to verify checksum
        String coreData = payload.isEmpty()
                ? messageType + "|" + seqStr
                : messageType + "|" + seqStr + "|" + payload;

        String expectedChecksum = checksumCalculator.calculate(coreData);
        boolean valid = expectedChecksum.equalsIgnoreCase(receivedChecksum.trim());

        if (!valid) {
            System.out.println("[FrameParser] ERROR: Checksum mismatch. Expected="
                    + expectedChecksum + " Received=" + receivedChecksum);
        }

        return new RobotFrame(messageType.trim(), sequenceId, payload,
                receivedChecksum.trim(), valid);
    }

    private RobotFrame invalid() {
        return new RobotFrame("", 0, "", "", false);
    }
}
