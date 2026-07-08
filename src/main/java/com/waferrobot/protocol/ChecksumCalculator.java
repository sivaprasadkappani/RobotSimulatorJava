package com.waferrobot.protocol;

/**
 * Calculates the XOR checksum used in the robot communication protocol.
 *
 * The checksum is calculated by XOR-ing every character in the core data
 * string, excluding SOH, CRLF, and the checksum field itself.
 */
public class ChecksumCalculator {

    /**
     * Calculates the XOR checksum for the given core data string.
     *
     * @param coreData the frame core data, e.g. "CMD|101|PICK FROM=LPA1 ARM=LOWER"
     * @return 2-character uppercase hexadecimal checksum string
     */
    public String calculate(String coreData) {
        int xor = 0;
        for (char c : coreData.toCharArray()) {
            xor ^= c;
        }
        return String.format("%02X", xor);
    }
}
