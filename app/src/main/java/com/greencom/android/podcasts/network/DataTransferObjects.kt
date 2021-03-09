package com.greencom.android.podcasts.network

import com.greencom.android.podcasts.data.database.GenreEntity
import com.greencom.android.podcasts.data.database.PodcastEntity
import com.greencom.android.podcasts.data.domain.Genre
import com.greencom.android.podcasts.data.domain.Podcast
import com.squareup.moshi.Json

/** Model class for `ListenApiService.searchEpisode` response. */
data class SearchEpisodeResponse(

    /** The number of search results in this page. */
    val count: Int,

    /** The total number of search results. */
    val total: Int,

    /** A list of search results. */
    @Json(name = "results")
    val episodes: List<SearchEpisodeResponseItem>,

    /**
     * Pass this value to the `offset` parameter of `searchEpisode()` to do
     * pagination of search results.
     */
    @Json(name = "next_offset")
    val nextOffset: Int,
) {

    /** Model class for a single episode object in the [SearchEpisodeResponse.episodes] list. */
    data class SearchEpisodeResponseItem(

        /** Episode ID. */
        val id: String,

        /** Episode title. */
        @Json(name = "title_original")
        val title: String,

        /** Episode description. */
        @Json(name = "description_original")
        val description: String,

        /** Image URL for this episode. */
        val image: String,

        /** Audio URL for this episode. */
        val audio: String,

        /** Audio length of this episode in seconds. */
        @Json(name = "audio_length_sec")
        val audioLength: Int,

        /** The podcast that this episode belongs to. */
        val podcast: SearchEpisodeResponseItemPodcast,

        /** Whether this podcast contains explicit language. */
        @Json(name = "explicit_content")
        val explicitContent: Boolean,

        /** Published date in millisecond. */
        @Json(name = "pub_date_ms")
        val date: Long,
    ) {

        /** Model class for a [SearchEpisodeResponse.SearchEpisodeResponseItem.podcast] object. */
        data class SearchEpisodeResponseItemPodcast(

            /** Podcast ID. */
            val id: String,

            /** Podcast name. */
            @Json(name = "title_original")
            val title: String,

            /** Image URL. */
            val image: String,

            /** Podcast publisher. */
            @Json(name = "publisher_original")
            val publisher: String,
        )
    }
}



/** Model class for `ListenApiService.searchPodcast` response. */
data class SearchPodcastResponse(

    /** The number of search results in this page. */
    val count: Int,

    /** The total number of search results. */
    val total: Int,

    /** A list of search results. */
    @Json(name = "results")
    val podcasts: List<SearchPodcastResponseItem>,

    /**
     * Pass this value to the `offset` parameter of `searchPodcast()` to do
     * pagination of search results.
     */
    @Json(name = "next_offset")
    val nextOffset: Int,
) {

    /** Model class for a single podcast object in the [SearchPodcastResponse.podcasts] list. */
    data class SearchPodcastResponseItem(

        /** Podcast ID. */
        val id: String,

        /** Podcast name. */
        @Json(name = "title_original")
        val title: String,

        /** Podcast description. */
        @Json(name = "description_original")
        val description: String,

        /** Image URL for this podcast. */
        val image: String,

        /** Podcast publisher. */
        @Json(name = "publisher_original")
        val publisher: String,

        /** Whether this podcast contains explicit language. */
        @Json(name = "explicit_content")
        val explicitContent: Boolean,

        /** Total number of episodes in this podcast. */
        @Json(name = "total_episodes")
        val episodeCount: Int,

        /** The published date of the latest episode of this podcast in milliseconds. */
        @Json(name = "latest_pub_date_ms")
        val latestPubDate: Long,
    )
}



/** Model class for `ListenApiService.getBestPodcasts()` response. */
data class BestPodcastsResponse(

    /** A list of search results. */
    val podcasts: List<BestPodcastsResponseItem>,

    /** Genre ID for which the best podcasts list is made for. */
    @Json(name = "id")
    val genreId: Int,

    /** Genre name. */
    @Json(name = "name")
    val genreName: String,

    /** Whether there is the next page of response. */
    @Json(name = "has_next")
    val hasNextPage: Boolean,
) {

    /** Model class for a single podcast object in the [BestPodcastsResponse.podcasts] list. */
    data class BestPodcastsResponseItem(

        /** Podcast ID. */
        val id: String,

        /** Podcast name. */
        val title: String,

        /** Podcast description. */
        val description: String,

        /** Image URL for this podcast. */
        val image: String,

        /** Podcast publisher. */
        val publisher: String,

        /** Whether this podcast contains explicit language. */
        @Json(name = "explicit_content")
        val explicitContent: Boolean,

        /** Total number of episodes in this podcast. */
        @Json(name = "total_episodes")
        val episodeCount: Int,

        /** The published date of the latest episode of this podcast in milliseconds. */
        @Json(name = "latest_pub_date_ms")
        val latestPubDate: Long,
    )
}

/** Convert [BestPodcastsResponse] object to a [PodcastEntity] list. */
fun BestPodcastsResponse.asPodcastEntities(): List<PodcastEntity> {
    return podcasts.map {
        PodcastEntity(
            id = it.id,
            title = it.title,
            description = it.description,
            image = it.image,
            publisher = it.publisher,
            explicitContent = it.explicitContent,
            episodeCount = it.episodeCount,
            latestPubDate = it.latestPubDate,
            inBestForGenre = this.genreId,
            inSubscriptions = false,
        )
    }
}

/** Convert [BestPodcastsResponse] object to a [Podcast] list. */
fun BestPodcastsResponse.asPodcasts(): List<Podcast> {
    return podcasts.map {
        Podcast(
            id = it.id,
            title = it.title,
            description = it.description,
            image = it.image,
            publisher = it.publisher,
            explicitContent = it.explicitContent,
            episodeCount = it.episodeCount,
            latestPubDate = it.latestPubDate,
            inBestForGenre = this.genreId,
            inSubscriptions = false,
        )
    }
}



/** Model class for `ListenApiService.getGenres()` response. */
data class GenresResponse(val genres: List<GenresResponseItem>) {

    /** Model class for a single genre object in the [GenresResponse.genres] list. */
    data class GenresResponseItem(

        /** Genre ID. */
        val id: Int,

        /** Genre name. */
        val name: String,

        /** Parent genre ID. */
        @Json(name = "parent_id")
        val parentId: Int?,
    )
}

/**
 * Convert [GenresResponse] object to a [GenreEntity] list.
 *
 * If [GenresResponse.GenresResponseItem.parentId] is `null`, assign [Genre.NO_PARENT_GENRE]
 * value to the [GenreEntity.parentId] property.
 */
fun GenresResponse.asGenreEntities(): List<GenreEntity> {
    return genres.map {
        GenreEntity(
            id = it.id,
            name = it.name,
            parentId = it.parentId ?: Genre.NO_PARENT_GENRE,
        )
    }
}

/**
 * Convert [GenresResponse] object to a [Genre] list.
 *
 * If [GenresResponse.GenresResponseItem.parentId] is `null`, assign [Genre.NO_PARENT_GENRE]
 * value to the [Genre.parentId] property.
 */
fun GenresResponse.asGenres(): List<Genre> {
    return genres.map {
        Genre(
            id = it.id,
            name = it.name,
            parentId = it.parentId ?: Genre.NO_PARENT_GENRE,
        )
    }
}