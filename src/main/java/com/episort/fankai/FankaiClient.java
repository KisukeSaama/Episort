package com.episort.fankai;

import java.util.List;

/** Read access to the Fankai catalog. Failures surface as {@link FankaiException}. */
public interface FankaiClient {
    List<FankaiSeries> series();

    List<FankaiEpisode> episodes(int seriesId);
}
