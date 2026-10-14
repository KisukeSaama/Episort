package com.episort.fankai;

import com.episort.config.JanusConfiguration;
import java.util.List;

/** Read access to the Fankai catalog. Failures surface as {@link FankaiException}. */
public interface FankaiClient {
    List<FankaiSeries> series(JanusConfiguration credentials);

    List<FankaiEpisode> episodes(int seriesId, JanusConfiguration credentials);
}
