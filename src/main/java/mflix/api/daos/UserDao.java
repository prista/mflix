package mflix.api.daos;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import mflix.api.models.Session;
import mflix.api.models.User;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.text.MessageFormat;
import java.util.Map;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Configuration
public class UserDao extends AbstractMFlixDao {

  private final MongoCollection<User> usersCollection;
  private final MongoCollection<Session> sessionsCollection;

  private final Logger log;

  @Autowired
  public UserDao(
      MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
    super(mongoClient, databaseName);
    CodecRegistry pojoCodecRegistry =
        fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            fromProviders(PojoCodecProvider.builder().automatic(true).build()));

    usersCollection = db.getCollection("users", User.class).withCodecRegistry(pojoCodecRegistry);
    log = LoggerFactory.getLogger(this.getClass());
    sessionsCollection = db.getCollection("sessions", Session.class).withCodecRegistry(pojoCodecRegistry);
  }

  /**
   * Inserts the `user` object in the `users` collection.
   *
   * @param user - User object to be added
   * @return True if successful, throw IncorrectDaoOperation otherwise
   */
  public boolean addUser(User user) {
    usersCollection.withWriteConcern(WriteConcern.MAJORITY).insertOne(user);
    return true;
    //TODO > Ticket: Handling Errors - make sure to only add new users
    // and not users that already exist.

  }

  /**
   * Creates session using userId and jwt token.
   *
   * @param userId - user string identifier
   * @param jwt - jwt string token
   * @return true if successful
   */
  public boolean createUserSession(String userId, String jwt) {
    Bson updateFilter = new Document("user_id", userId);
    Bson setUpdate = Updates.set("jwt", jwt);
    UpdateOptions options = new UpdateOptions().upsert(true);
    sessionsCollection.updateOne(updateFilter, setUpdate, options);
    return true;
    //TODO > Ticket: Handling Errors - implement a safeguard against
    // creating a session with the same jwt token.
  }

  /**
   * Returns the User object matching the an email string value.
   *
   * @param email - email string to be matched.
   * @return User object or null.
   */
  public User getUser(String email) {
    Bson queryFilter = new Document("email", email);
    return usersCollection.find(queryFilter).first();
  }

  /**
   * Given the userId, returns a Session object.
   *
   * @param userId - user string identifier.
   * @return Session object or null.
   */
  public Session getUserSession(String userId) {
    Bson queryFilter = new Document("user_id", userId);
    return sessionsCollection.find(queryFilter).iterator().tryNext();
  }

  public boolean deleteUserSessions(String userId) {
    Bson queryFilter = new Document("user_id", userId);
    sessionsCollection.deleteOne(queryFilter);
    return !sessionsCollection.find(queryFilter).iterator().hasNext();
  }

  /**
   * Removes the user document that match the provided email.
   *
   * @param email - of the user to be deleted.
   * @return true if user successfully removed
   */
  public boolean deleteUser(String email) {
    boolean isExistSession = sessionsCollection.find(new Document("user_id", email)).iterator().hasNext();
    sessionsCollection.deleteOne(new Document("user_id", email));
    usersCollection.deleteOne(new Document("email", email));
    //TODO > Ticket: Handling Errors - make this method more robust by
    // handling potential exceptions.
    return !usersCollection.find(new Document("email", email)).iterator().hasNext();
  }

  /**
   * Updates the preferences of an user identified by `email` parameter.
   *
   * @param email - user to be updated email
   * @param userPreferences - set of preferences that should be stored and replace the existing
   *     ones. Cannot be set to null value
   * @return User object that just been updated.
   */
  public boolean updateUserPreferences(String email, Map<String, ?> userPreferences) {
    if (userPreferences == null) {
      throw new IncorrectDaoOperation(String.format("%s cannot be null", userPreferences));
    }
    Bson queryFilter = new Document("email", email);
    User user = usersCollection.find(queryFilter).limit(1).iterator().tryNext();
    user.setPreferences((Map<String, String>) userPreferences);
    usersCollection.replaceOne(queryFilter, user);
    //TODO > Ticket: Handling Errors - make this method more robust by
    // handling potential exceptions when updating an entry.
    return true;
  }
}
