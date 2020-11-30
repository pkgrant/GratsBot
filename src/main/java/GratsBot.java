import Audio.LevelUpAudioSelector;
import com.maphus.kafa.API.ProfileAPI.CharacterProfileAPI;
import com.maphus.kafa.DTOs.CharacterProfileSummary;
import com.maphus.kafa.DTOs.Token;
import com.maphus.kafa.API.Authorization;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.voice.AudioProvider;
import io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue;

import java.util.*;

public class GratsBot {

    private static final Map<String, Command> commands = new HashMap<>();

    public static void main(String[] args) {

        // Get our token from blizzard API
        Token blizzToken = Authorization.generateToken(args[1], args[2]);

        // Setup audio
        // Creates AudioPlayer instances and translates URLs to AudioTrack instances
        final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        // Optimization strategy
        playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        playerManager.registerSourceManager(new LocalAudioSourceManager());

        // Create AudioPlay so Discord4J can receive audio data
        final AudioPlayer player = playerManager.createPlayer();
        final TrackScheduler scheduler = new TrackScheduler(player);
        AudioProvider provider = new LavaPlayerAudioProvider(player);

        // Build command set
        commands.put("ding", event -> {
            final Member member = event.getMember().orElse(null);
            if(member != null) {
                final VoiceState voiceState = member.getVoiceState().block();
                if(voiceState != null) {
                    final VoiceChannel channel = voiceState.getChannel().block();
                    if(channel != null) {
                        channel.join(spec -> spec.setProvider(provider)).block();
                        playerManager.loadItem(LevelUpAudioSelector.randomLevelUpVoice(new Random().nextInt(2)), scheduler);
                    }
                }
            }
        });

        commands.put("summary", event -> {
            final String content = event.getMessage().getContent();
            final List<String> command = Arrays.asList(content.split(" "));
            MessagePassingQueue.Consumer<EmbedCreateSpec> template = embed -> {};

            if(command.size() < 3) {
                event.getMessage().getChannel().block()
                    .createMessage(spec -> spec.setEmbed(embed -> {
                        embed.addField("Usage", "!summary {character} {realm}", true);
                        template.accept(embed);
                })).block();
            }
            else {
                CharacterProfileSummary summary = CharacterProfileAPI.getCharacterProfileSummary(
                        command.get(2).toLowerCase(), command.get(1).toLowerCase(), blizzToken.getAccessToken());

                if(summary.getLevel() == -1) {
                    //any check will do
                    event.getMessage().getChannel().block()
                        .createMessage(spec -> spec.setEmbed(embed -> {
                            embed.addField("Error", "Character not found!", true);
                            template.accept(embed);
                        })).block();
                }
                else {
                    event.getMessage().getChannel().block()
                        .createMessage(spec -> spec.setEmbed(embed -> {
                            embed.addField(summary.getName(), summary.getRealm(), true);
                            embed.addField("Item Level", Integer.toString(summary.getEquippedItemLevel()), false);
                            embed.addField("Character Level", Integer.toString(summary.getLevel()), false);
                            template.accept(embed);
                        })).block();
                }
            }

        });

        // Build the Discord Client
        final String discordToken = args[0];
        final DiscordClient client = DiscordClient.create(discordToken);
        final GatewayDiscordClient gateway = client.login().block();

        // Command Handler
        gateway.getEventDispatcher().on(MessageCreateEvent.class)
                .subscribe(event -> {
                    final String content = event.getMessage().getContent();
                    for (final Map.Entry<String, Command> entry : commands.entrySet()) {
                        if(content.startsWith('!' + entry.getKey())) {
                            entry.getValue().execute(event);
                            break;
                        }
                    }
                });

        gateway.onDisconnect().block();
    }

    static {
        commands.put("grats", event -> event.getMessage()
                .getChannel().block()
                .createMessage("Grats!").block());

    }
}
