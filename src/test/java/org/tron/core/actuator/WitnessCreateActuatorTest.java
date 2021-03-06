package org.tron.core.actuator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.core.Constant;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j

public class WitnessCreateActuatorTest {

  private static AnnotationConfigApplicationContext context;
  private static Manager dbManager;
  private static Any contract;
  private static final String dbPath = "output_CreateAccountTest";

  private static final String ACCOUNT_NAME_FRIST = "ownerF";
  private static final String OWNER_ADDRESS_FRIST =
      Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
  private static final String ACCOUNT_NAME_SECOND = "ownerS";
  private static final String OWNER_ADDRESS_SECOND =
      Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
  private static final String URL = "https://tron.network";
  private static final String OWNER_ADDRESS_INVALIATE = "aaaa";
  private static final String OWNER_ADDRESS_NOACCOUNT =
      Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1aed";
  private static final String OWNER_ADDRESS_NALANCENOTSUFFIENT =
      Wallet.getAddressPreFixString() + "548794500882809695a8a687866e06d4271a1ced";

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new AnnotationConfigApplicationContext(DefaultConfig.class);
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);

  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void createCapsule() {
    WitnessCapsule ownerCapsule =
        new WitnessCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)),
            10_000_000L,
            URL);
    AccountCapsule ownerAccountSecondCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(ACCOUNT_NAME_SECOND),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)),
            AccountType.Normal,
            300_000_000L);
    AccountCapsule ownerAccountFirstCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(ACCOUNT_NAME_FRIST),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_FRIST)),
            AccountType.Normal,
            200_000_000L);

    dbManager.getAccountStore()
        .put(ownerAccountSecondCapsule.getAddress().toByteArray(), ownerAccountSecondCapsule);
    dbManager.getAccountStore()
        .put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);

    dbManager.getWitnessStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
    dbManager.getWitnessStore().delete(ByteArray.fromHexString(OWNER_ADDRESS_FRIST));
  }

  private Any getContract(String address, String url) {
    return Any.pack(
        Contract.WitnessCreateContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
            .setUrl(ByteString.copyFrom(ByteArray.fromString(url)))
            .build());
  }

  /**
   * first createWitness,result is success.
   */
  @Test
  public void firstCreateWitness() {
    WitnessCreateActuator actuator =
        new WitnessCreateActuator(getContract(OWNER_ADDRESS_FRIST, URL), dbManager);
    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS_FRIST));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      WitnessCapsule witnessCapsule =
          dbManager.getWitnessStore().get(ByteArray.fromHexString(OWNER_ADDRESS_FRIST));
      Assert.assertNotNull(witnessCapsule);
      Assert.assertEquals(
          witnessCapsule.getInstance().getUrl(),
          URL);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * second createWitness,result is failed,exception is "Witness has existed".
   */
  @Test
  public void secondCreateAccount() {
    WitnessCreateActuator actuator =
        new WitnessCreateActuator(getContract(OWNER_ADDRESS_SECOND, URL), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Witness[" + OWNER_ADDRESS_SECOND + "] has existed", e.getMessage());

    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use Invalidate Address createWitness,result is failed,exception is "Invalidate address".
   */
  @Test
  public void invalidateAddress() {
    WitnessCreateActuator actuator =
        new WitnessCreateActuator(getContract(OWNER_ADDRESS_INVALIATE, URL), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Invalidate address");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);

      Assert.assertEquals("Invalidate address", e.getMessage());

    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  /**
   * use AccountStore not exists Address createWitness,result is failed,exception is "account not
   * exists".
   */
  @Test
  public void noAccount() {
    WitnessCreateActuator actuator =
        new WitnessCreateActuator(getContract(OWNER_ADDRESS_NOACCOUNT, URL), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("account[+OWNER_ADDRESS_NOACCOUNT+] not exists");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  /**
   * use Account  ,result is failed,exception is "account not exists".
   */
  @Test
  public void balanceNotSufficient() {
    AccountCapsule balanceNotSufficientCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("balanceNotSufficient"),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_NALANCENOTSUFFIENT)),
            AccountType.Normal,
            50L);

    dbManager.getAccountStore()
        .put(balanceNotSufficientCapsule.getAddress().toByteArray(), balanceNotSufficientCapsule);
    WitnessCreateActuator actuator =
        new WitnessCreateActuator(getContract(OWNER_ADDRESS_NALANCENOTSUFFIENT, URL), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("witnessAccount  has balance[" + balanceNotSufficientCapsule.getBalance()
          + "] < MIN_BALANCE[100]");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("witnessAccount  has balance[" + balanceNotSufficientCapsule.getBalance()
          + "] < MIN_BALANCE[100]", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * Release resources.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
    context.destroy();
  }
}