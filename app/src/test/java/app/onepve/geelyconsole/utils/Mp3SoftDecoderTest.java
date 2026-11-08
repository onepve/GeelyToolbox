package app.onepve.geelyconsole.utils;

import org.junit.Test;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import static org.junit.Assert.*;

/**
 * 验证 JLayer 纯 Java 解码器在各 MP3 资产上的真实解码正确性
 */
public class Mp3SoftDecoderTest {

    @Test
    public void testBuiltinMp3DecodingIntegrity() throws Exception {
        File audioDir = new File("src/main/assets/audio");
        assertTrue("Audio assets directory must exist", audioDir.exists() && audioDir.isDirectory());

        File[] mp3s = audioDir.listFiles((dir, name) -> name.endsWith(".mp3"));
        assertNotNull("MP3 assets array must not be null", mp3s);
        assertEquals("Must contain all 22 official MP3 voice files", 22, mp3s.length);

        for (File mp3 : mp3s) {
            try (FileInputStream fis = new FileInputStream(mp3);
                 BufferedInputStream bis = new BufferedInputStream(fis, 16 * 1024)) {
                
                Bitstream bitstream = new Bitstream(bis);
                Decoder decoder = new Decoder();

                Header header = bitstream.readFrame();
                assertNotNull("First MP3 header should be valid for " + mp3.getName(), header);

                int sampleRate = header.frequency();
                int channels = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;

                assertTrue("Sample rate must be valid standard audio freq for " + mp3.getName(), sampleRate > 0);
                assertTrue("Channels must be 1 or 2 for " + mp3.getName(), channels == 1 || channels == 2);

                int decodedFrames = 0;
                int totalSamples = 0;

                while (header != null) {
                    SampleBuffer sampleBuffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    assertNotNull("SampleBuffer must not be null for " + mp3.getName(), sampleBuffer);

                    short[] pcm = sampleBuffer.getBuffer();
                    int pcmLen = sampleBuffer.getBufferLength();

                    assertTrue("PCM data must be decoded for " + mp3.getName(), pcmLen > 0);
                    assertTrue("PCM buffer size must accommodate length", pcm.length >= pcmLen);

                    totalSamples += pcmLen;
                    decodedFrames++;

                    bitstream.closeFrame();
                    header = bitstream.readFrame();
                }

                assertTrue("Decoded frames must be greater than zero for " + mp3.getName(), decodedFrames > 0);
                assertTrue("Total decoded 16-bit PCM samples must be positive for " + mp3.getName(), totalSamples > 0);
            }
        }
    }
}
