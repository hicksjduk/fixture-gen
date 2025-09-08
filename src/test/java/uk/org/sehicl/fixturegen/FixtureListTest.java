package uk.org.sehicl.fixturegen;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Test;

import uk.org.sehicl.website.data.Match;
import uk.org.sehicl.website.data.Model;
import uk.org.sehicl.website.data.Team;

public class FixtureListTest
{
    @Test
    public void testNoDuplicateTimesAndPlaces()
    {
        Model model = FixtureGenerator.model();
        Function<Match, String> dateTimeKey = m -> String.format("%tc%s", m.getDateTime(),
                m.getCourt());
        model.getLeagues().stream().forEach(l ->
        {
            final List<Match> ml = l.getMatches();
            final int size = ml.size();
            final String name = l.getName();
            int expectedSize = Arrays.asList("Division 1", "Division 2").contains(name) ? 30 : 45;
            assertThat(size).as(name).isEqualTo(expectedSize);
            assertThat(ml.stream().map(dateTimeKey).collect(Collectors.toSet()).size())
                    .as(name)
                    .isEqualTo(size);
        });
        final List<Match> matches = model
                .getLeagues()
                .stream()
                .flatMap(l -> l.getMatches().stream())
                .collect(Collectors.toList());
        assertThat(matches.size()).isEqualTo(285);
        Set<String> keys = new HashSet<>();
        matches.stream().map(dateTimeKey).forEach(k ->
        {
            assertThat(keys.contains(k)).as(k).isFalse();
            keys.add(k);
        });
        assertThat(keys.size()).isEqualTo(matches.size());
    }

    @Test
    public void testAllTeamsHaveCorrectFixtureList()
    {
        Model model = FixtureGenerator.model();
        model.getLeagues().stream().forEach(league ->
        {
            String name = league.getName();
            final Set<String> teamIds = league
                    .getTeams()
                    .stream()
                    .map(Team::getId)
                    .collect(Collectors.toSet());
            int oppCount = teamIds.size() - 1;
            assertThat(oppCount).as(name).isIn(5, 9);
            int matchesEach = oppCount == 5 ? 2 : 1;
            league
                    .getMatches()
                    .stream()
                    .flatMap(m -> pairings(m).entrySet().stream())
                    .collect(Collectors.groupingBy(Entry::getKey))
                    .forEach((teamId, opps) ->
                    {
                        assertThat(opps.size()).as(teamId).isEqualTo(oppCount * matchesEach);
                        assertThat(teamId).isNotIn(opps);
                        assertThat(teamId).isIn(teamIds);
                        opps.stream().collect(Collectors.groupingBy(Entry::getValue)).forEach(
                                (t, l) ->
                                {
                                    assertThat(t).isIn(teamIds);
                                    assertThat(l.size()).as(teamId).isEqualTo(matchesEach);
                                });
                    });
        });
    }

    private Map<String, String> pairings(Match m)
    {
        Map<String, String> answer = new HashMap<>();
        answer.put(m.getHomeTeamId(), m.getAwayTeamId());
        answer.put(m.getAwayTeamId(), m.getHomeTeamId());
        return answer;
    }
}
