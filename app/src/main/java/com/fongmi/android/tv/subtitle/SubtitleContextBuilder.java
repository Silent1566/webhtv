package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.subtitle.model.ResolvedMediaIdentity;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleRequest;

public final class SubtitleContextBuilder {

    private final SubtitleTmdbResolver resolver;
    private final SubtitleTitleParser parser;

    public SubtitleContextBuilder() {
        this(new SubtitleTmdbResolver(), new SubtitleTitleParser());
    }

    SubtitleContextBuilder(SubtitleTmdbResolver resolver, SubtitleTitleParser parser) {
        this.resolver = resolver;
        this.parser = parser;
    }

    public SubtitleContext build(SubtitleRequest request) {
        ResolvedMediaIdentity identity = resolver.resolve(request);
        String canonicalTitle = !SubtitleStrings.isEmpty(identity.getCanonicalTitle()) ? identity.getCanonicalTitle() : parser.cleanTitle(request.getVodName());
        String originalTitle = identity.getOriginalTitle();
        int year = identity.getYear() > 0 ? identity.getYear() : parser.firstYear(request.getVodYear());
        int seasonNumber = identity.getSeasonNumber() > 0 ? identity.getSeasonNumber() : request.getSeasonNumber();
        int episodeNumber = identity.getEpisodeNumber() > 0 ? identity.getEpisodeNumber() : request.getEpisodeNumber();
        String episodeTitle = !SubtitleStrings.isEmpty(identity.getEpisodeTitle()) ? identity.getEpisodeTitle() : parser.cleanTitle(request.getEpisodeName());
        String originalLanguage = request.getTmdbItem() == null ? "" : request.getTmdbItem().getOriginalLanguage();
        String originCountry = request.getTmdbItem() == null ? "" : request.getTmdbItem().getOriginCountry();

        SubtitleContext.Builder builder = SubtitleContext.builder()
                .playbackKey(request.getPlaybackKey())
                .siteKey(request.getSiteKey())
                .vodId(request.getVodId())
                .mediaType(identity.getMediaType())
                .canonicalTitle(canonicalTitle)
                .originalTitle(originalTitle)
                .year(year)
                .seasonNumber(seasonNumber)
                .episodeNumber(episodeNumber)
                .episodeTitle(episodeTitle)
                .preferredLanguage(request.getPreferredLanguage())
                .originalLanguage(originalLanguage)
                .originCountry(originCountry)
                .identity(identity)
                .networkStream(true);

        for (String alias : parser.aliases(request.getVodName(), request.getVodRemarks(), request.getEpisodeName())) builder.addAlias(alias);
        return builder.build();
    }
}
