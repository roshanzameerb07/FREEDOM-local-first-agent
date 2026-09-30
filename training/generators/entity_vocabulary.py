"""
Synthetic Indian Farmer Entity Vocabulary.
Provides partitioned names for Train, Validation, Test, and Hard Test splits.
Ensures zero entity contamination across splits.
"""

from typing import List, Tuple

# Training partition names (~100 names across 1-word, 2-part, 3-part, initials)
TRAIN_NAMES = [
    "Suresh", "Ramesh", "Mahesh", "Ganesh", "Manjunath",
    "Basavaraj", "Chandrashekar", "Shivanna", "Ninge Gowda", "Siddaiah",
    "Anand Kumar", "Bhyrappa", "Chennappa", "Dyavanna", "Eregowda",
    "Gopal Krishna", "Hanumanthappa", "Ishwarappa", "Jayanna", "Krishna Murthy",
    "Lingappa", "Mallikarjun", "Nagaraj", "Paramesh", "Ramegowda",
    "Somanna", "Thimmegowda", "Umesh", "Venkatesh", "Yallappa",
    "Appaji Gowda", "Boregowda", "Chikkanna", "Devendrappa", "Govindappa",
    "Honnegowda", "Kallappa", "Lakshmana", "Mayanna", "Ningappa",
    "Puttegowda", "Ranganatha", "Shivalingaiah", "Thippeswamy", "Veeranna",
    "Abhishek", "Basappa", "Chidananda", "Dharani Kumar", "Girish",
    "Hemant Kumar", "Jagadeesh", "Kempanna", "Lokappa", "Marigowda",
    "Nanjegowda", "Praveen", "Ravi Kumar", "Siddaramaiah", "Thammanna",
    "K. Suresh", "M. Ramesh", "B. Mahesh", "T. Shivanna", "C. Nagaraj",
    "Smt. Lakshmi", "Parvathamma", "Gowramma", "Kamalamma", "Ningamma",
    "Jayamma", "Savithramma", "Bhagyamma", "Manjula", "Shanthamma",
    "K. R. Suresh", "H. M. Ramesh", "T. K. Shivanna", "B. C. Nagaraj", "S. P. Kumar",
    "Rajegowda M", "Sanjeevappa K", "Venkataramanappa", "Doddaboregowda", "Chikkasiddaiah",
    "Basavegowda", "Eshwarappa Gowda", "Halappa Naik", "Kariyappa", "Muddegowda",
    "Narayanaswamy", "Papanna Gowda", "Rudrappa", "Shivananjegowda", "Veerabhadraiah"
]

# Validation partition names (25 names, disjoint from Train and Test)
VAL_NAMES = [
    "Kiran Kumar", "Santosh Naik", "Balaraju", "Chinnaswamy", "Danappa",
    "Gangaraju", "Haleshappa", "Jagannath", "Kotresh", "Mahadevappa",
    "Naveen Gowda", "Prashanth", "Raghavendra", "Shashidhar", "Tharanath",
    "Vijay Kumar", "K. N. Ravi", "P. M. Basavaraj", "Smt. Rathnamma", "Meenakshi",
    "H. K. Anand", "D. M. Siddappa", "Rangaswamy", "Kenchaiah", "Boramma"
]

# Test partition names (Held-out evaluation, disjoint from Train, Val, and Hard-Test)
TEST_NAMES = [
    "Arun Kumar", "Bheeshmaiah", "Chetan Gowda", "Dharmendra", "Girish Naik",
    "Harish Babu", "Indrakumar", "Jithendra", "Kumaraswamy", "Lokeshappa",
    "Muddappa Gowda", "Nataraja", "Pradeep Kumar", "Rajashekar", "Subramanya",
    "Thimme Gowda H", "Vasanth Kumar", "Yogesh", "B. V. Prasad", "C. S. Jayadev",
    "Smt. Padmavathi", "Geethamma", "Hemavathi", "Devamma", "Channamma",
    "K. L. Nanjappa", "R. T. Basappa", "M. K. Thimmaiah", "S. B. Ranganath", "N. V. Siddanna"
]

# Hard Test partition names (Completely held-out, challenging orthography/initials/rare syntax)
HARD_TEST_NAMES = [
    "Dr. K. V. Ramaswamy", "H. D. Deve Gowda", "Prof. C. N. Rao", "Sri K. H. Ramegowda",
    "Smt. B. M. Shanthamma", "Chikka Channabasappa", "Dodda Veerabhadrappa", "Kari Siddegowda",
    "K. S. L. Narayana Rao", "T. P. R. Venkatesh Murthy", "A. B. C. Gowda", "V. K. R. V. Rao",
    "M. S. Swaminathan", "B. S. Yediyurappa", "S. R. Bommai", "J. H. Patel",
    "N. Dharam Singh", "K. C. Reddy", "Kengal Hanumanthaiah", "Kadidal Manjappa",
    "S. Nijalingappa", "D. Devaraj Urs", "R. Gundu Rao", "Ramakrishna Hegde",
    "S. Bangarappa", "M. Veerappa Moily", "H. D. Kumaraswamy", "B. S. Yeddiyur",
    "Sidda Ramaiah Naik", "Mallikarjuna Kharge Gowda"
]

# Partitioned Unknown / Unregistered Farmer Names (100% pairwise disjoint from each other and all registered lists)
TRAIN_UNKNOWN_NAMES = [
    "Pooja", "Deepak", "Aakash", "Priyanka", "Kavitha",
    "Rohit", "Sunil", "Vikas", "Amit", "Neha",
    "Shweta", "Aniket", "Sachin", "Swati", "Manoj",
    "Ashok", "Kishore", "Rajani", "Sangeetha", "Vinay",
    "Gautami", "Tanmay", "Harsha", "Sneha", "Kalyan",
    "Mukesh", "Pratibha", "Sharath", "Uday", "Varsha"
]

VAL_UNKNOWN_NAMES = [
    "Anitha", "Madhu", "Siddharth", "Rekha", "Vinod Kumar",
    "Geeta Bai", "Varun", "Rashmi", "Chetana", "Tarun",
    "Madhav", "Kavya Shree", "Chaitra", "Dhanush", "Bhavana"
]

TEST_UNKNOWN_NAMES = [
    "Sanjay", "Pallavi", "Gautam", "Meena Kumari", "Tejas",
    "Divya", "Sumanth", "Aparna", "Raghu", "Nandini",
    "Vidyadhar", "Roopa", "Pramod", "Kusuma", "Darshan",
    "Shilpa", "Guruprasad", "Leelavathi", "Chandrakanth", "Padma"
]

HARD_TEST_UNKNOWN_NAMES = [
    "Dr. Arvind", "Prof. K. Swamy", "Smt. K. V. Lalitha", "B. K. Hariprasad", "Sri R. K. Patil",
    "Capt. V. Mohan", "Dr. S. Radhakrishnan Gowda", "Smt. T. Susheela", "Justice M. Rama Jois", "Pandit V. D. Shastri",
    "Major N. Somesh", "Dr. B. R. Ambedkar Naik", "Smt. Indiramma K", "Sri H. K. Veeranna", "M/s Cauvery Dairy Farm",
    "Swamy Agnivesh", "Smt. Gangubai Hanagal", "Master K. Karthik", "Kumari S. Deepa", "Lt. Col. P. Deshpande"
]

def get_unknown_name(split: str, variant_idx: int) -> str:
    """Returns a realistic unregistered name guaranteed disjoint for the specified split."""
    if split == "train":
        names = TRAIN_UNKNOWN_NAMES
    elif split == "val" or split == "validation":
        names = VAL_UNKNOWN_NAMES
    elif split == "test":
        names = TEST_UNKNOWN_NAMES
    elif split == "hard_test":
        names = HARD_TEST_UNKNOWN_NAMES
    else:
        names = TRAIN_UNKNOWN_NAMES
    return names[variant_idx % len(names)]

# Ambiguous pairs: sharing common first token but distinct identities
AMBIGUOUS_PAIRS: List[Tuple[str, List[str]]] = [
    ("Suresh", ["Suresh Kumar", "Suresh Gowda"]),
    ("Ramesh", ["Ramesh Naik", "Ramesh Babu"]),
    ("Mahesh", ["Mahesh Patil", "Mahesh Hegde"]),
    ("Manjunath", ["Manjunath K", "Manjunath H"]),
    ("Anand", ["Anand Kumar", "Anand Rao"]),
    ("Gopal", ["Gopal Krishna", "Gopal Gowda"]),
    ("Shiva", ["Shivanna Naik", "Shivakumar Gowda"]),
    ("Nagaraj", ["Nagaraj M", "Nagaraj C"]),
    ("Lakshmi", ["Smt. Lakshmi Devi", "Smt. Lakshmi Bai"]),
    ("Basavaraj", ["Basavaraj Bommai", "Basavaraj Patil"])
]

# Punctuation and casing variations generator
def apply_orthographic_variation(name: str, variant_idx: int) -> str:
    """Applies realistic farmer name variation: lowercase, UPPERCASE, omitted dots, extra spaces."""
    mode = variant_idx % 5
    if mode == 0:
        return name
    elif mode == 1:
        return name.lower()
    elif mode == 2:
        return name.upper()
    elif mode == 3:
        # omit dots in initials: e.g. "K. Suresh" -> "K Suresh"
        return name.replace(".", "")
    elif mode == 4:
        # trim or double space
        return name.replace(" ", "  ")
    return name
