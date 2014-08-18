package org.javers.core

import org.javers.core.diff.changetype.ValueChange
import org.javers.core.model.DummyAddress
import org.javers.core.model.DummyUser
import org.javers.core.model.SnapshotEntity
import org.javers.core.snapshot.SnapshotsAssert
import spock.lang.Specification
import spock.lang.Unroll

import static org.javers.core.JaversBuilder.javers
import static org.javers.core.metamodel.object.InstanceIdDTO.instanceId
import static org.javers.core.metamodel.object.ValueObjectIdDTO.valueObjectId
import static org.javers.test.builder.DummyUserBuilder.dummyUser

class JaversRepositoryIntegrationTest extends Specification {

    Javers javers

    def setup() {
        // InMemoryRepository is used by default
        javers = javers().build()
    }

    def "should store state history of Entity instance in JaversRepository and fetch snapshots in reverse order"() {
        given:
        def ref = new SnapshotEntity(id:2)
        def cdo = new SnapshotEntity(id: 1, entityRef: ref)
        javers.commit("author",cdo) //v. 1
        ref.intProperty = 5
        javers.commit("author2",cdo) //v. 2

        when:
        def snapshots = javers.getStateHistory(2, SnapshotEntity, 10)

        then:
        def cdoId = instanceId(2,SnapshotEntity)
        SnapshotsAssert.assertThat(snapshots)
                .hasSize(2)
                .hasSnapshot(cdoId, "1.0", [id:2])
                .hasSnapshot(cdoId, "2.0", [id:2, intProperty:5])

        snapshots[0].commitId == "2.0"
        snapshots[0].commitMetadata.author == "author2"
        snapshots[0].commitMetadata.commitDate
        snapshots[1].commitId == "1.0"
        snapshots[1].commitMetadata.author == "author"
        snapshots[1].commitMetadata.commitDate
    }

    def "should compare Entity property values with latest from repository"() {
        given:
        def user = dummyUser("John").withAge(18).build()
        javers.commit("login", user)

        when:
        user.age = 19
        javers.commit("login", user)
        def history = javers.getChangeHistory("John", DummyUser, 100)

        then:
        history.size() == 1
        history[0] instanceof ValueChange
        history[0].affectedCdoId == instanceId("John", DummyUser)
        history[0].property.name == "age"
        history[0].left == 18
        history[0].right == 19
    }

    def "should compare ValueObject property values with latest from repository"() {
        given:
        def cdo = new SnapshotEntity(id: 1, listOfValueObjects: [new DummyAddress("London","street")])
        javers.commit("login", cdo)

        when:
        cdo.listOfValueObjects[0].city = "Paris"
        javers.commit("login", cdo)
        def voId = valueObjectId(1, SnapshotEntity, "listOfValueObjects/0")
        def history = javers.getChangeHistory(voId, 100)

        then:
        history.size() == 1
        history[0] instanceof ValueChange
        with(history[0]) {
            affectedCdoId == voId
            property.name == "city"
            left == "London"
            right == "Paris"
        }
    }

    @Unroll
    def "should store snapshot of #what and find it by id"() {
        given:
        def cdo = new SnapshotEntity(id: 1, listOfValueObjects: [new DummyAddress("London")])
        javers.commit("login", cdo)

        when:
        def snapshot = javers.getLatestSnapshot(givenId).get()

        then:
        snapshot.globalId == givenId
        snapshot.getPropertyValue(property) == expextedState

        where:
        what <<    ["Entity instance", "ValueObject"]
        givenId << [instanceId(1, SnapshotEntity), valueObjectId(1, SnapshotEntity, "listOfValueObjects/0")]
        property << ["id","city"]
        expextedState << [1,"London"]
     }
}