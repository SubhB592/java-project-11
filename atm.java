import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

enum TransactionType { DEPOSIT, WITHDRAWAL, TRANSFER_IN, TRANSFER_OUT }

class Transaction {
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final TransactionType type;
    private final double amount;
    private final double balanceAfter;
    private final LocalDateTime time;

    public Transaction(TransactionType type, double amount, double balanceAfter) {
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.time = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }

    @Override
    public String toString() {
        return String.format("%-20s %-13s Rs.%10.2f   Bal: Rs.%10.2f",
                time.format(FMT), type, amount, balanceAfter);
    }
}

class Account {
    private static final double MIN_BALANCE = 500.0;
    private static final double DAILY_WITHDRAW_LIMIT = 20000.0;

    private final String cardNumber;
    private final String holderName;
    private int pin;
    private double balance;
    private boolean blocked;
    private double withdrawnToday;
    private final List<Transaction> transactions = new ArrayList<>();

    public Account(String cardNumber, String holderName, int pin, double balance) {
        this.cardNumber = cardNumber;
        this.holderName = holderName;
        this.pin = pin;
        this.balance = balance;
    }

    public String getCardNumber() { return cardNumber; }
    public String getHolderName() { return holderName; }
    public double getBalance()    { return balance; }
    public boolean isBlocked()    { return blocked; }
    public void block()           { blocked = true; }

    public boolean verifyPin(int enteredPin) {
        return this.pin == enteredPin;
    }

    public void changePin(int newPin) {
        this.pin = newPin;
    }

    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive.");
        balance += amount;
        transactions.add(new Transaction(TransactionType.DEPOSIT, amount, balance));
    }

    public void withdraw(double amount) throws InsufficientFundsException {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive.");
        if (amount % 100 != 0)
            throw new IllegalArgumentException("Amount must be in multiples of Rs.100.");
        if (withdrawnToday + amount > DAILY_WITHDRAW_LIMIT)
            throw new IllegalArgumentException(String.format(
                    "Daily limit exceeded. You can withdraw Rs.%.2f more today.",
                    DAILY_WITHDRAW_LIMIT - withdrawnToday));
        if (balance - amount < MIN_BALANCE)
            throw new InsufficientFundsException(String.format(
                    "Insufficient funds. Minimum balance of Rs.%.2f must be maintained.",
                    MIN_BALANCE));
        balance -= amount;
        withdrawnToday += amount;
        transactions.add(new Transaction(TransactionType.WITHDRAWAL, amount, balance));
    }

    public void transferTo(Account target, double amount) throws InsufficientFundsException {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive.");
        if (target == this) throw new IllegalArgumentException("Cannot transfer to the same account.");
        if (balance - amount < MIN_BALANCE)
            throw new InsufficientFundsException(String.format(
                    "Insufficient funds. Minimum balance of Rs.%.2f must be maintained.",
                    MIN_BALANCE));
        balance -= amount;
        target.balance += amount;
        transactions.add(new Transaction(TransactionType.TRANSFER_OUT, amount, balance));
        target.transactions.add(new Transaction(TransactionType.TRANSFER_IN, amount, target.balance));
    }

    public List<Transaction> getLastTransactions(int n) {
        int from = Math.max(0, transactions.size() - n);
        return transactions.subList(from, transactions.size());
    }
}

class Bank {
    private final Map<String, Account> accounts = new HashMap<>();

    public Bank() {
        addAccount(new Account("1111222233334444", "Rahul Sharma", 1234, 25000));
        addAccount(new Account("5555666677778888", "Priya Patel", 4321, 50000));
        addAccount(new Account("9999000011112222", "Amit Das", 1111, 8000));
    }

    public final void addAccount(Account acc) {
        accounts.put(acc.getCardNumber(), acc);
    }

    public Account findAccount(String cardNumber) {
        return accounts.get(cardNumber);
    }
}

class ATM {
    private static final int MAX_PIN_ATTEMPTS = 3;

    private final Bank bank;
    private final Scanner sc;

    public ATM(Bank bank, Scanner sc) {
        this.bank = bank;
        this.sc = sc;
    }

    public void start() {
        while (true) {
            printBanner();
            System.out.print("Insert card (enter 16-digit card number) or 'exit': ");
            String card = sc.nextLine().trim();
            if (card.equalsIgnoreCase("exit")) {
                System.out.println("\nThank you for using JavaBank ATM. Goodbye!");
                return;
            }

            Account acc = bank.findAccount(card);
            if (acc == null) {
                System.out.println(">> Invalid card number. Please try again.\n");
                continue;
            }
            if (acc.isBlocked()) {
                System.out.println(">> This card is blocked. Please contact your branch.\n");
                continue;
            }

            if (authenticate(acc)) {
                session(acc);
            }
        }
    }

    private boolean authenticate(Account acc) {
        for (int attempt = 1; attempt <= MAX_PIN_ATTEMPTS; attempt++) {
            int pin = readInt("Enter 4-digit PIN: ");
            if (acc.verifyPin(pin)) {
                System.out.println("\n>> Welcome, " + acc.getHolderName() + "!");
                return true;
            }
            int left = MAX_PIN_ATTEMPTS - attempt;
            if (left > 0) {
                System.out.println(">> Wrong PIN. Attempts left: " + left);
            }
        }
        acc.block();
        System.out.println(">> Too many wrong attempts. Your card has been BLOCKED.\n");
        return false;
    }

    private void session(Account acc) {
        while (true) {
            System.out.println("\n========== MAIN MENU ==========");
            System.out.println("1. Balance Enquiry");
            System.out.println("2. Cash Withdrawal");
            System.out.println("3. Cash Deposit");
            System.out.println("4. Fund Transfer");
            System.out.println("5. Mini Statement");
            System.out.println("6. Change PIN");
            System.out.println("7. Exit (Remove Card)");
            System.out.println("===============================");

            int choice = readInt("Choose an option: ");
            try {
                switch (choice) {
                    case 1 -> showBalance(acc);
                    case 2 -> withdraw(acc);
                    case 3 -> deposit(acc);
                    case 4 -> transfer(acc);
                    case 5 -> miniStatement(acc);
                    case 6 -> changePin(acc);
                    case 7 -> {
                        System.out.println("\n>> Please collect your card. Thank you!\n");
                        return;
                    }
                    default -> System.out.println(">> Invalid option. Try again.");
                }
            } catch (InsufficientFundsException | IllegalArgumentException e) {
                System.out.println(">> Transaction failed: " + e.getMessage());
            }
        }
    }

    private void showBalance(Account acc) {
        System.out.printf(">> Available balance: Rs.%.2f%n", acc.getBalance());
    }

    private void withdraw(Account acc) throws InsufficientFundsException {
        System.out.println("Quick cash: 1) 500  2) 1000  3) 2000  4) 5000  5) Other amount");
        int opt = readInt("Select: ");
        double amount = switch (opt) {
            case 1 -> 500;
            case 2 -> 1000;
            case 3 -> 2000;
            case 4 -> 5000;
            case 5 -> readDouble("Enter amount (multiple of 100): ");
            default -> throw new IllegalArgumentException("Invalid selection.");
        };
        acc.withdraw(amount);
        System.out.printf(">> Please collect Rs.%.2f. Remaining balance: Rs.%.2f%n",
                amount, acc.getBalance());
    }

    private void deposit(Account acc) {
        double amount = readDouble("Enter amount to deposit: ");
        acc.deposit(amount);
        System.out.printf(">> Rs.%.2f deposited. New balance: Rs.%.2f%n",
                amount, acc.getBalance());
    }

    private void transfer(Account acc) throws InsufficientFundsException {
        System.out.print("Enter recipient's card number: ");
        String targetCard = sc.nextLine().trim();
        Account target = bank.findAccount(targetCard);
        if (target == null) {
            throw new IllegalArgumentException("Recipient account not found.");
        }
        double amount = readDouble("Enter amount to transfer: ");
        System.out.printf("Confirm transfer of Rs.%.2f to %s? (y/n): ",
                amount, target.getHolderName());
        if (!sc.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println(">> Transfer cancelled.");
            return;
        }
        acc.transferTo(target, amount);
        System.out.printf(">> Transferred Rs.%.2f to %s. Remaining balance: Rs.%.2f%n",
                amount, target.getHolderName(), acc.getBalance());
    }

    private void miniStatement(Account acc) {
        List<Transaction> list = acc.getLastTransactions(5);
        System.out.println("\n----------------- MINI STATEMENT -----------------");
        System.out.println("Account holder: " + acc.getHolderName());
        System.out.println("Card: XXXX-XXXX-XXXX-" + acc.getCardNumber().substring(12));
        System.out.println("--------------------------------------------------");
        if (list.isEmpty()) {
            System.out.println("No transactions yet.");
        } else {
            list.forEach(System.out::println);
        }
        System.out.printf("Current balance: Rs.%.2f%n", acc.getBalance());
        System.out.println("--------------------------------------------------");
    }

    private void changePin(Account acc) {
        int oldPin = readInt("Enter current PIN: ");
        if (!acc.verifyPin(oldPin)) {
            throw new IllegalArgumentException("Incorrect current PIN.");
        }
        int newPin = readInt("Enter new 4-digit PIN: ");
        if (newPin < 1000 || newPin > 9999) {
            throw new IllegalArgumentException("PIN must be exactly 4 digits.");
        }
        int confirm = readInt("Re-enter new PIN: ");
        if (newPin != confirm) {
            throw new IllegalArgumentException("PINs do not match.");
        }
        acc.changePin(newPin);
        System.out.println(">> PIN changed successfully.");
    }

    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                int value = sc.nextInt();
                sc.nextLine();
                return value;
            } catch (InputMismatchException e) {
                sc.nextLine();
                System.out.println(">> Please enter a valid number.");
            }
        }
    }

    private double readDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                double value = sc.nextDouble();
                sc.nextLine();
                return value;
            } catch (InputMismatchException e) {
                sc.nextLine();
                System.out.println(">> Please enter a valid amount.");
            }
        }
    }

    private void printBanner() {
        System.out.println("==================================================");
        System.out.println("            WELCOME TO JAVABANK ATM               ");
        System.out.println("==================================================");
    }
}

public class ATMSimulation {
    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            ATM atm = new ATM(new Bank(), sc);
            atm.start();
        }
    }
}
