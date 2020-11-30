package Audio;

public class LevelUpAudioSelector {
    final static String ORC_MALE = "./src/main/resources/OrcMale.wav";
    final static String PANDA_MALE = "./src/main/resources/PandaMale.wav";

    public static String randomLevelUpVoice(int number) {
        switch (number) {
            case 0:
                return ORC_MALE;
            case 1:
                return PANDA_MALE;
            default:
                return ORC_MALE;
        }
    }
}
