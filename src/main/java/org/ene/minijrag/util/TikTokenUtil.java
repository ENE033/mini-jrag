package org.ene.minijrag.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
public class TikTokenUtil {
    private static final Encoding encoding;


    static {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        encoding = registry.getEncoding(ModelType.GPT_4.getEncodingType());
        log.info("TikTokenUtil initialized with cl100k_base encoding");
    }

    public static int countTokens(String text) {
        return encoding.countTokens(text);
    }

    public static List<Integer> encode(String text) {
        return encoding.encode(text);
    }

    public static String decode(List<Integer> tokenIds) {
        return encoding.decode(tokenIds);
    }
}
